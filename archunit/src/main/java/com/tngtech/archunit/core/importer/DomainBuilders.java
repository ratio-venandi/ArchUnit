/*
 * Copyright 2014-2020 TNG Technology Consulting GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tngtech.archunit.core.importer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.tngtech.archunit.Internal;
import com.tngtech.archunit.base.HasDescription;
import com.tngtech.archunit.base.Optional;
import com.tngtech.archunit.core.domain.AccessTarget;
import com.tngtech.archunit.core.domain.AccessTarget.ConstructorCallTarget;
import com.tngtech.archunit.core.domain.AccessTarget.FieldAccessTarget;
import com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget;
import com.tngtech.archunit.core.domain.DomainObjectCreationContext;
import com.tngtech.archunit.core.domain.Formatters;
import com.tngtech.archunit.core.domain.InstanceofCheck;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClassDescriptor;
import com.tngtech.archunit.core.domain.JavaClassList;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaStaticInitializer;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.JavaTypeVariable;
import com.tngtech.archunit.core.domain.JavaWildcardType;
import com.tngtech.archunit.core.domain.Source;
import com.tngtech.archunit.core.domain.ThrowsClause;
import com.tngtech.archunit.core.importer.DomainBuilders.JavaAnnotationBuilder.ValueBuilder;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Sets.union;
import static com.tngtech.archunit.core.domain.DomainObjectCreationContext.completeTypeVariable;
import static com.tngtech.archunit.core.domain.DomainObjectCreationContext.createInstanceofCheck;
import static com.tngtech.archunit.core.domain.DomainObjectCreationContext.createJavaClassList;
import static com.tngtech.archunit.core.domain.DomainObjectCreationContext.createSource;
import static com.tngtech.archunit.core.domain.DomainObjectCreationContext.createThrowsClause;
import static com.tngtech.archunit.core.domain.DomainObjectCreationContext.createTypeVariable;
import static com.tngtech.archunit.core.domain.DomainObjectCreationContext.createWildcardType;
import static com.tngtech.archunit.core.domain.JavaConstructor.CONSTRUCTOR_NAME;

@Internal
public final class DomainBuilders {
    private DomainBuilders() {
    }

    static <T extends HasDescription> Map<String, JavaAnnotation<T>> buildAnnotations(T owner, Set<JavaAnnotationBuilder> annotations, ClassesByTypeName importedClasses) {
        ImmutableMap.Builder<String, JavaAnnotation<T>> result = ImmutableMap.builder();
        for (JavaAnnotationBuilder annotationBuilder : annotations) {
            JavaAnnotation<T> javaAnnotation = annotationBuilder.build(owner, importedClasses);
            result.put(javaAnnotation.getRawType().getName(), javaAnnotation);
        }
        return result.build();
    }

    @Internal
    public static final class JavaEnumConstantBuilder {
        private JavaClass declaringClass;
        private String name;

        JavaEnumConstantBuilder() {
        }

        JavaEnumConstantBuilder withDeclaringClass(final JavaClass declaringClass) {
            this.declaringClass = declaringClass;
            return this;
        }

        JavaEnumConstantBuilder withName(final String name) {
            this.name = name;
            return this;
        }

        public JavaClass getDeclaringClass() {
            return declaringClass;
        }

        public String getName() {
            return name;
        }

        JavaEnumConstant build() {
            return DomainObjectCreationContext.createJavaEnumConstant(this);
        }
    }

    @Internal
    public abstract static class JavaMemberBuilder<OUTPUT, SELF extends JavaMemberBuilder<OUTPUT, SELF>>
            implements BuilderWithBuildParameter<JavaClass, OUTPUT> {

        private String name;
        private String descriptor;
        private Set<JavaAnnotationBuilder> annotations;
        private Set<JavaModifier> modifiers;
        private JavaClass owner;
        private ClassesByTypeName importedClasses;
        private int firstLineNumber;

        private JavaMemberBuilder() {
        }

        SELF withName(String name) {
            this.name = name;
            return self();
        }

        SELF withDescriptor(String descriptor) {
            this.descriptor = descriptor;
            return self();
        }

        SELF withAnnotations(Set<JavaAnnotationBuilder> annotations) {
            this.annotations = annotations;
            return self();
        }

        SELF withModifiers(Set<JavaModifier> modifiers) {
            this.modifiers = modifiers;
            return self();
        }

        void recordLineNumber(int lineNumber) {
            this.firstLineNumber = this.firstLineNumber == 0 ? lineNumber : Math.min(this.firstLineNumber, lineNumber);
        }

        @SuppressWarnings("unchecked")
        SELF self() {
            return (SELF) this;
        }

        abstract OUTPUT construct(SELF self, ClassesByTypeName importedClasses);

        JavaClass get(String typeName) {
            return importedClasses.get(typeName);
        }

        public String getName() {
            return name;
        }

        public Supplier<Map<String, JavaAnnotation<JavaMember>>> getAnnotations(final JavaMember owner) {
            return Suppliers.memoize(new Supplier<Map<String, JavaAnnotation<JavaMember>>>() {
                @Override
                public Map<String, JavaAnnotation<JavaMember>> get() {
                    return buildAnnotations(owner, annotations, importedClasses);
                }
            });
        }

        public String getDescriptor() {
            return descriptor;
        }

        public Set<JavaModifier> getModifiers() {
            return modifiers;
        }

        public JavaClass getOwner() {
            return owner;
        }

        public int getFirstLineNumber() {
            return firstLineNumber;
        }

        @Override
        public final OUTPUT build(JavaClass owner, ClassesByTypeName importedClasses) {
            this.owner = owner;
            this.importedClasses = importedClasses;
            return construct(self(), importedClasses);
        }
    }

    @Internal
    public static final class JavaFieldBuilder extends JavaMemberBuilder<JavaField, JavaFieldBuilder> {
        private JavaClassDescriptor type;

        JavaFieldBuilder() {
        }

        JavaFieldBuilder withType(JavaClassDescriptor type) {
            this.type = type;
            return self();
        }

        public JavaClass getType() {
            return get(type.getFullyQualifiedClassName());
        }

        @Override
        JavaField construct(JavaFieldBuilder builder, ClassesByTypeName importedClasses) {
            return DomainObjectCreationContext.createJavaField(builder);
        }
    }

    @Internal
    public abstract static class JavaCodeUnitBuilder<OUTPUT, SELF extends JavaCodeUnitBuilder<OUTPUT, SELF>> extends JavaMemberBuilder<OUTPUT, SELF> {
        private JavaClassDescriptor returnType;
        private List<JavaClassDescriptor> parameters;
        private List<JavaClassDescriptor> throwsDeclarations;
        private final List<RawInstanceofCheck> instanceOfChecks = new ArrayList<>();

        private JavaCodeUnitBuilder() {
        }

        SELF withReturnType(JavaClassDescriptor type) {
            returnType = type;
            return self();
        }

        SELF withParameters(List<JavaClassDescriptor> parameters) {
            this.parameters = parameters;
            return self();
        }

        SELF withThrowsClause(List<JavaClassDescriptor> throwsDeclarations) {
            this.throwsDeclarations = throwsDeclarations;
            return self();
        }

        SELF addInstanceOfCheck(RawInstanceofCheck rawInstanceOfChecks) {
            this.instanceOfChecks.add(rawInstanceOfChecks);
            return self();
        }

        public JavaClass getReturnType() {
            return get(returnType.getFullyQualifiedClassName());
        }

        public JavaClassList getParameters() {
            return createJavaClassList(asJavaClasses(parameters));
        }

        public <CODE_UNIT extends JavaCodeUnit> ThrowsClause<CODE_UNIT> getThrowsClause(CODE_UNIT codeUnit) {
            return createThrowsClause(codeUnit, asJavaClasses(this.throwsDeclarations));
        }

        public Set<InstanceofCheck> getInstanceofChecks(JavaCodeUnit codeUnit) {
            ImmutableSet.Builder<InstanceofCheck> result = ImmutableSet.builder();
            for (RawInstanceofCheck instanceOfCheck : this.instanceOfChecks) {
                result.add(createInstanceofCheck(codeUnit, get(instanceOfCheck.getTarget().getFullyQualifiedClassName()), instanceOfCheck.getLineNumber()));
            }
            return result.build();
        }

        private List<JavaClass> asJavaClasses(List<JavaClassDescriptor> descriptors) {
            ImmutableList.Builder<JavaClass> result = ImmutableList.builder();
            for (JavaClassDescriptor javaClassDescriptor : descriptors) {
                result.add(get(javaClassDescriptor.getFullyQualifiedClassName()));
            }
            return result.build();
        }
    }

    @Internal
    public static final class JavaMethodBuilder extends JavaCodeUnitBuilder<JavaMethod, JavaMethodBuilder> {
        private Optional<ValueBuilder> annotationDefaultValueBuilder = Optional.absent();
        private Supplier<Optional<Object>> annotationDefaultValue = Suppliers.ofInstance(Optional.absent());

        JavaMethodBuilder() {
        }

        JavaMethodBuilder withAnnotationDefaultValue(ValueBuilder defaultValue) {
            annotationDefaultValueBuilder = Optional.of(defaultValue);
            return this;
        }

        public Supplier<Optional<Object>> getAnnotationDefaultValue() {
            return annotationDefaultValue;
        }

        @Override
        JavaMethod construct(JavaMethodBuilder builder, final ClassesByTypeName importedClasses) {
            if (annotationDefaultValueBuilder.isPresent()) {
                annotationDefaultValue = Suppliers.memoize(new DefaultValueSupplier(getOwner(), annotationDefaultValueBuilder.get(), importedClasses));
            }
            return DomainObjectCreationContext.createJavaMethod(builder);
        }

        private static class DefaultValueSupplier implements Supplier<Optional<Object>> {
            private final JavaClass owner;
            private final ValueBuilder valueBuilder;
            private final ClassesByTypeName importedClasses;

            DefaultValueSupplier(JavaClass owner, ValueBuilder valueBuilder, ClassesByTypeName importedClasses) {
                this.owner = owner;
                this.valueBuilder = valueBuilder;
                this.importedClasses = importedClasses;
            }

            @Override
            public Optional<Object> get() {
                return valueBuilder.build(owner, importedClasses);
            }
        }
    }

    @Internal
    public static final class JavaConstructorBuilder extends JavaCodeUnitBuilder<JavaConstructor, JavaConstructorBuilder> {
        JavaConstructorBuilder() {
        }

        @Override
        JavaConstructor construct(JavaConstructorBuilder builder, ClassesByTypeName importedClasses) {
            return DomainObjectCreationContext.createJavaConstructor(builder);
        }
    }

    @Internal
    public static final class JavaClassBuilder {
        private Optional<SourceDescriptor> sourceDescriptor = Optional.absent();
        private Optional<String> sourceFileName = Optional.absent();
        private JavaClassDescriptor descriptor;
        private boolean isInterface;
        private boolean isEnum;
        private boolean isAnonymousClass;
        private boolean isMemberClass;
        private Set<JavaModifier> modifiers = new HashSet<>();

        JavaClassBuilder() {
        }

        JavaClassBuilder withSourceDescriptor(SourceDescriptor sourceDescriptor) {
            this.sourceDescriptor = Optional.of(sourceDescriptor);
            return this;
        }

        JavaClassBuilder withSourceFileName(String sourceFileName) {
            this.sourceFileName = Optional.of(sourceFileName);
            return this;
        }

        JavaClassBuilder withDescriptor(JavaClassDescriptor descriptor) {
            this.descriptor = descriptor;
            return this;
        }

        JavaClassBuilder withInterface(boolean isInterface) {
            this.isInterface = isInterface;
            return this;
        }

        JavaClassBuilder withAnonymousClass(boolean isAnonymousClass) {
            this.isAnonymousClass = isAnonymousClass;
            return this;
        }

        JavaClassBuilder withMemberClass(boolean isMemberClass) {
            this.isMemberClass = isMemberClass;
            return this;
        }

        JavaClassBuilder withEnum(boolean isEnum) {
            this.isEnum = isEnum;
            return this;
        }

        JavaClassBuilder withModifiers(Set<JavaModifier> modifiers) {
            this.modifiers = modifiers;
            return this;
        }

        JavaClassBuilder withSimpleName(String simpleName) {
            this.descriptor = descriptor.withSimpleClassName(simpleName);
            return this;
        }

        JavaClass build() {
            return DomainObjectCreationContext.createJavaClass(this);
        }

        public Optional<Source> getSource() {
            return sourceDescriptor.isPresent()
                    ? Optional.of(createSource(sourceDescriptor.get().getUri(), sourceFileName, sourceDescriptor.get().isMd5InClassSourcesEnabled()))
                    : Optional.<Source>absent();
        }

        public JavaClassDescriptor getDescriptor() {
            return descriptor;
        }

        public boolean isInterface() {
            return isInterface;
        }

        public boolean isEnum() {
            return isEnum;
        }

        public boolean isAnonymousClass() {
            return isAnonymousClass;
        }

        public boolean isMemberClass() {
            return isMemberClass;
        }

        public Set<JavaModifier> getModifiers() {
            return modifiers;
        }
    }

    @Internal
    public static final class JavaAnnotationBuilder {
        private JavaClassDescriptor type;
        private final Map<String, ValueBuilder> values = new LinkedHashMap<>();
        private ClassesByTypeName importedClasses;

        JavaAnnotationBuilder() {
        }

        JavaAnnotationBuilder withType(JavaClassDescriptor type) {
            this.type = type;
            return this;
        }

        JavaClassDescriptor getTypeDescriptor() {
            return type;
        }

        String getFullyQualifiedClassName() {
            return type.getFullyQualifiedClassName();
        }

        JavaAnnotationBuilder addProperty(String key, ValueBuilder valueBuilder) {
            values.put(key, valueBuilder);
            return this;
        }

        public JavaClass getType() {
            return importedClasses.get(type.getFullyQualifiedClassName());
        }

        public <T extends HasDescription> Map<String, Object> getValues(T owner) {
            ImmutableMap.Builder<String, Object> result = ImmutableMap.builder();
            for (Map.Entry<String, ValueBuilder> entry : values.entrySet()) {
                Optional<Object> value = entry.getValue().build(owner, importedClasses);
                if (value.isPresent()) {
                    result.put(entry.getKey(), value.get());
                }
            }
            addDefaultValues(result, importedClasses);
            return result.build();
        }

        private void addDefaultValues(ImmutableMap.Builder<String, Object> result, ClassesByTypeName importedClasses) {
            for (JavaMethod method : importedClasses.get(type.getFullyQualifiedClassName()).getMethods()) {
                if (!values.containsKey(method.getName()) && method.getDefaultValue().isPresent()) {
                    result.put(method.getName(), method.getDefaultValue().get());
                }
            }
        }

        public <T extends HasDescription> JavaAnnotation<T> build(T owner, ClassesByTypeName importedClasses) {
            this.importedClasses = importedClasses;
            return DomainObjectCreationContext.createJavaAnnotation(owner, this);
        }

        abstract static class ValueBuilder {
            abstract <T extends HasDescription> Optional<Object> build(T owner, ClassesByTypeName importedClasses);

            static ValueBuilder ofFinished(final Object value) {
                return new ValueBuilder() {
                    @Override
                    <T extends HasDescription> Optional<Object> build(T owner, ClassesByTypeName importedClasses) {
                        return Optional.of(value);
                    }
                };
            }

            static ValueBuilder from(final JavaAnnotationBuilder builder) {
                return new ValueBuilder() {
                    @Override
                    <T extends HasDescription> Optional<Object> build(T owner, ClassesByTypeName importedClasses) {
                        return Optional.<Object>of(builder.build(owner, importedClasses));
                    }
                };
            }
        }
    }

    @Internal
    public static final class JavaStaticInitializerBuilder extends JavaCodeUnitBuilder<JavaStaticInitializer, JavaStaticInitializerBuilder> {
        JavaStaticInitializerBuilder() {
            withReturnType(JavaClassDescriptor.From.name(void.class.getName()));
            withParameters(Collections.<JavaClassDescriptor>emptyList());
            withName(JavaStaticInitializer.STATIC_INITIALIZER_NAME);
            withDescriptor("()V");
            withAnnotations(Collections.<JavaAnnotationBuilder>emptySet());
            withModifiers(Collections.<JavaModifier>emptySet());
            withThrowsClause(Collections.<JavaClassDescriptor>emptyList());
        }

        @Override
        JavaStaticInitializer construct(JavaStaticInitializerBuilder builder, ClassesByTypeName importedClasses) {
            return DomainObjectCreationContext.createJavaStaticInitializer(builder);
        }
    }

    interface JavaTypeCreationProcess {
        JavaType finish(Iterable<JavaTypeVariable> allTypeParametersInContext, ClassesByTypeName classes);
    }

    @Internal
    public static final class JavaTypeParameterBuilder {
        private final String name;
        private final List<JavaTypeCreationProcess> upperBounds = new ArrayList<>();
        private ClassesByTypeName importedClasses;

        JavaTypeParameterBuilder(String name) {
            this.name = checkNotNull(name);
        }

        void addBound(JavaTypeCreationProcess bound) {
            upperBounds.add(bound);
        }

        public JavaTypeVariable build(ClassesByTypeName importedClasses) {
            this.importedClasses = importedClasses;
            return createTypeVariable(name, this.importedClasses.get(Object.class.getName()));
        }

        String getName() {
            return name;
        }

        public List<JavaType> getUpperBounds(Iterable<JavaTypeVariable> allGenericParametersInContext) {
            return buildJavaTypes(upperBounds, allGenericParametersInContext, importedClasses);
        }
    }

    static class TypeParametersBuilder {
        private final Collection<JavaTypeParameterBuilder> typeParameterBuilders;

        TypeParametersBuilder(Collection<JavaTypeParameterBuilder> typeParameterBuilders) {
            this.typeParameterBuilders = typeParameterBuilders;
        }

        public List<JavaTypeVariable> build(JavaClass owner, ClassesByTypeName classesByTypeName) {
            if (typeParameterBuilders.isEmpty()) {
                return Collections.emptyList();
            }

            Map<JavaTypeVariable, JavaTypeParameterBuilder> typeArgumentsToBuilders = new LinkedHashMap<>();
            for (JavaTypeParameterBuilder builder : typeParameterBuilders) {
                typeArgumentsToBuilders.put(builder.build(classesByTypeName), builder);
            }
            Set<JavaTypeVariable> allGenericParametersInContext = union(allTypeParametersInEnclosingClassesOf(owner), typeArgumentsToBuilders.keySet());
            for (Map.Entry<JavaTypeVariable, JavaTypeParameterBuilder> typeParameterToBuilder : typeArgumentsToBuilders.entrySet()) {
                List<JavaType> upperBounds = typeParameterToBuilder.getValue().getUpperBounds(allGenericParametersInContext);
                completeTypeVariable(typeParameterToBuilder.getKey(), upperBounds);
            }
            return ImmutableList.copyOf(typeArgumentsToBuilders.keySet());
        }

        private Set<JavaTypeVariable> allTypeParametersInEnclosingClassesOf(JavaClass javaClass) {
            Set<JavaTypeVariable> result = new HashSet<>();
            while (javaClass.getEnclosingClass().isPresent()) {
                result.addAll(javaClass.getEnclosingClass().get().getTypeParameters());
                javaClass = javaClass.getEnclosingClass().get();
            }
            return result;
        }
    }

    interface JavaTypeBuilder {
        JavaType build(Iterable<JavaTypeVariable> allTypeParametersInContext, ClassesByTypeName importedClasses);
    }

    @Internal
    public static final class JavaWildcardTypeBuilder implements JavaTypeBuilder {
        private final List<JavaTypeCreationProcess> lowerBoundCreationProcesses = new ArrayList<>();
        private final List<JavaTypeCreationProcess> upperBoundCreationProcesses = new ArrayList<>();
        private Iterable<JavaTypeVariable> allTypeParametersInContext;
        private ClassesByTypeName importedClasses;

        JavaWildcardTypeBuilder() {
        }

        public JavaWildcardTypeBuilder addLowerBound(JavaTypeCreationProcess boundCreationProcess) {
            lowerBoundCreationProcesses.add(boundCreationProcess);
            return this;
        }

        public JavaWildcardTypeBuilder addUpperBound(JavaTypeCreationProcess boundCreationProcess) {
            upperBoundCreationProcesses.add(boundCreationProcess);
            return this;
        }

        @Override
        public JavaWildcardType build(Iterable<JavaTypeVariable> allTypeParametersInContext, ClassesByTypeName importedClasses) {
            this.allTypeParametersInContext = allTypeParametersInContext;
            this.importedClasses = importedClasses;
            return createWildcardType(this);
        }

        public List<JavaType> getUpperBounds() {
            return buildJavaTypes(upperBoundCreationProcesses, allTypeParametersInContext, importedClasses);
        }

        public List<JavaType> getLowerBounds() {
            return buildJavaTypes(lowerBoundCreationProcesses, allTypeParametersInContext, importedClasses);
        }

        public JavaClass getUnboundErasureType(List<JavaType> upperBounds) {
            return DomainBuilders.getUnboundErasureType(upperBounds, importedClasses);
        }
    }

    static class JavaParameterizedTypeBuilder implements JavaTypeBuilder {
        private final JavaClassDescriptor type;
        private final List<JavaTypeCreationProcess> typeArgumentCreationProcesses = new ArrayList<>();

        JavaParameterizedTypeBuilder(JavaClassDescriptor type) {
            this.type = type;
        }

        void addTypeArgument(JavaTypeCreationProcess typeCreationProcess) {
            typeArgumentCreationProcesses.add(typeCreationProcess);
        }

        @Override
        public JavaParameterizedType build(Iterable<JavaTypeVariable> allTypeParametersInContext, ClassesByTypeName classes) {
            List<JavaType> typeArguments = buildJavaTypes(typeArgumentCreationProcesses, allTypeParametersInContext, classes);
            return new ImportedParameterizedType(classes.get(type.getFullyQualifiedClassName()), typeArguments);
        }

        String getTypeName() {
            return type.getFullyQualifiedClassName();
        }
    }

    private static List<JavaType> buildJavaTypes(List<? extends JavaTypeCreationProcess> typeCreationProcesses, Iterable<JavaTypeVariable> allGenericParametersInContext, ClassesByTypeName classes) {
        ImmutableList.Builder<JavaType> result = ImmutableList.builder();
        for (JavaTypeCreationProcess typeCreationProcess : typeCreationProcesses) {
            result.add(typeCreationProcess.finish(allGenericParametersInContext, classes));
        }
        return result.build();
    }

    private static JavaClass getUnboundErasureType(List<JavaType> upperBounds, ClassesByTypeName importedClasses) {
        return upperBounds.size() > 0
                ? upperBounds.get(0).toErasure()
                : importedClasses.get(Object.class.getName());
    }

    @Internal
    interface BuilderWithBuildParameter<PARAMETER, VALUE> {
        VALUE build(PARAMETER parameter, ClassesByTypeName importedClasses);

        @Internal
        class BuildFinisher {
            static <PARAMETER, VALUE> Set<VALUE> build(
                    Set<? extends BuilderWithBuildParameter<PARAMETER, ? extends VALUE>> builders,
                    PARAMETER parameter,
                    ClassesByTypeName importedClasses) {
                checkNotNull(builders);
                checkNotNull(parameter);

                ImmutableSet.Builder<VALUE> result = ImmutableSet.builder();
                for (BuilderWithBuildParameter<PARAMETER, ? extends VALUE> builder : builders) {
                    result.add(builder.build(parameter, importedClasses));
                }
                return result.build();
            }
        }
    }

    @Internal
    public abstract static class JavaAccessBuilder<TARGET extends AccessTarget, SELF extends JavaAccessBuilder<TARGET, SELF>> {
        private JavaCodeUnit origin;
        private TARGET target;
        private int lineNumber;

        private JavaAccessBuilder() {
        }

        SELF withOrigin(final JavaCodeUnit origin) {
            this.origin = origin;
            return self();
        }

        SELF withTarget(final TARGET target) {
            this.target = target;
            return self();
        }

        SELF withLineNumber(final int lineNumber) {
            this.lineNumber = lineNumber;
            return self();
        }

        public JavaCodeUnit getOrigin() {
            return origin;
        }

        public TARGET getTarget() {
            return target;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        @SuppressWarnings("unchecked")
        private SELF self() {
            return (SELF) this;
        }
    }

    @Internal
    public static class JavaFieldAccessBuilder extends JavaAccessBuilder<FieldAccessTarget, JavaFieldAccessBuilder> {
        private AccessType accessType;

        JavaFieldAccessBuilder() {
        }

        JavaFieldAccessBuilder withAccessType(final AccessType accessType) {
            this.accessType = accessType;
            return this;
        }

        public AccessType getAccessType() {
            return accessType;
        }

        JavaFieldAccess build() {
            return DomainObjectCreationContext.createJavaFieldAccess(this);
        }
    }

    @Internal
    public static final class JavaMethodCallBuilder extends JavaAccessBuilder<MethodCallTarget, JavaMethodCallBuilder> {
        JavaMethodCallBuilder() {
        }

        JavaMethodCall build() {
            return DomainObjectCreationContext.createJavaMethodCall(this);
        }
    }

    @Internal
    public static class JavaConstructorCallBuilder extends JavaAccessBuilder<ConstructorCallTarget, JavaConstructorCallBuilder> {
        JavaConstructorCallBuilder() {
        }

        JavaConstructorCall build() {
            return DomainObjectCreationContext.createJavaConstructorCall(this);
        }
    }

    abstract static class AccessTargetBuilder<SELF extends AccessTargetBuilder<SELF>> {
        private JavaClass owner;
        private String name;

        private AccessTargetBuilder() {
        }

        SELF withOwner(final JavaClass owner) {
            this.owner = owner;
            return self();
        }

        SELF withName(final String name) {
            this.name = name;
            return self();
        }

        public JavaClass getOwner() {
            return owner;
        }

        public String getName() {
            return name;
        }

        @SuppressWarnings("unchecked")
        SELF self() {
            return (SELF) this;
        }
    }

    @Internal
    public static final class FieldAccessTargetBuilder extends AccessTargetBuilder<FieldAccessTargetBuilder> {
        private JavaClass type;
        private Supplier<Optional<JavaField>> field;

        FieldAccessTargetBuilder() {
        }

        FieldAccessTargetBuilder withType(final JavaClass type) {
            this.type = type;
            return this;
        }

        FieldAccessTargetBuilder withField(final Supplier<Optional<JavaField>> field) {
            this.field = field;
            return this;
        }

        public JavaClass getType() {
            return type;
        }

        public Supplier<Optional<JavaField>> getField() {
            return field;
        }

        public String getFullName() {
            return getOwner().getName() + "." + getName();
        }

        FieldAccessTarget build() {
            return DomainObjectCreationContext.createFieldAccessTarget(this);
        }
    }

    @Internal
    public abstract static class CodeUnitCallTargetBuilder<SELF extends CodeUnitCallTargetBuilder<SELF>>
            extends AccessTargetBuilder<SELF> {
        private JavaClassList parameters;
        private JavaClass returnType;

        private CodeUnitCallTargetBuilder() {
        }

        SELF withParameters(final JavaClassList parameters) {
            this.parameters = parameters;
            return self();
        }

        SELF withReturnType(final JavaClass returnType) {
            this.returnType = returnType;
            return self();
        }

        public JavaClassList getParameters() {
            return parameters;
        }

        public JavaClass getReturnType() {
            return returnType;
        }

        public String getFullName() {
            return Formatters.formatMethod(getOwner().getName(), getName(), parameters);
        }
    }

    @Internal
    public static final class ConstructorCallTargetBuilder extends CodeUnitCallTargetBuilder<ConstructorCallTargetBuilder> {
        private Supplier<Optional<JavaConstructor>> constructor;

        ConstructorCallTargetBuilder() {
            withName(CONSTRUCTOR_NAME);
        }

        ConstructorCallTargetBuilder withConstructor(Supplier<Optional<JavaConstructor>> constructor) {
            this.constructor = constructor;
            return self();
        }

        public Supplier<Optional<JavaConstructor>> getConstructor() {
            return constructor;
        }

        ConstructorCallTarget build() {
            return DomainObjectCreationContext.createConstructorCallTarget(this);
        }
    }

    @Internal
    public static final class MethodCallTargetBuilder extends CodeUnitCallTargetBuilder<MethodCallTargetBuilder> {
        private Supplier<Set<JavaMethod>> methods;

        MethodCallTargetBuilder() {
        }

        MethodCallTargetBuilder withMethods(final Supplier<Set<JavaMethod>> methods) {
            this.methods = methods;
            return this;
        }

        public Supplier<Set<JavaMethod>> getMethods() {
            return methods;
        }

        MethodCallTarget build() {
            return DomainObjectCreationContext.createMethodCallTarget(this);
        }
    }

    private static class ImportedParameterizedType implements JavaParameterizedType {
        private final JavaType type;
        private final List<JavaType> typeArguments;

        ImportedParameterizedType(JavaType type, List<JavaType> typeArguments) {
            this.type = type;
            this.typeArguments = typeArguments;
        }

        @Override
        public String getName() {
            return type.getName();
        }

        @Override
        public JavaClass toErasure() {
            return type.toErasure();
        }

        @Override
        public List<JavaType> getActualTypeArguments() {
            return typeArguments;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "{" + type.getName() + formatTypeArguments() + '}';
        }

        private String formatTypeArguments() {
            return typeArguments.isEmpty() ? "" : "<" + Joiner.on(", ").join(typeArguments) + ">";
        }
    }
}
