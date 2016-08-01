package com.tngtech.archunit.integration;

import com.tngtech.archunit.example.SomeMediator;
import com.tngtech.archunit.example.controller.one.UseCaseOneController;
import com.tngtech.archunit.example.controller.two.UseCaseTwoController;
import com.tngtech.archunit.example.persistence.layerviolation.DaoCallingService;
import com.tngtech.archunit.example.service.ServiceViolatingLayerRules;
import com.tngtech.archunit.exampletest.LayerDependencyRulesTest;
import com.tngtech.archunit.junit.ExpectedViolation;
import org.junit.Rule;
import org.junit.Test;

import static com.tngtech.archunit.example.SomeMediator.violateLayerRulesIndirectly;
import static com.tngtech.archunit.example.controller.one.UseCaseOneController.someString;
import static com.tngtech.archunit.example.controller.two.UseCaseTwoController.doSomethingTwo;
import static com.tngtech.archunit.example.persistence.layerviolation.DaoCallingService.violateLayerRules;
import static com.tngtech.archunit.example.service.ServiceViolatingLayerRules.illegalAccessToController;
import static com.tngtech.archunit.junit.ExpectedViolation.from;

public class LayerDependencyRulesIntegrationTest extends LayerDependencyRulesTest {

    @Rule
    public final ExpectedViolation expectViolation = ExpectedViolation.none();

    @Test
    @Override
    public void services_should_not_access_controllers() {
        expectViolation.ofRule("classes that reside in '..service..' " +
                "should never access classes that reside in '..controller..'")
                .byAccess(from(ServiceViolatingLayerRules.class, illegalAccessToController)
                        .getting().field(UseCaseOneController.class, someString)
                        .inLine(11))
                .byCall(from(ServiceViolatingLayerRules.class, illegalAccessToController)
                        .toConstructor(UseCaseTwoController.class)
                        .inLine(12))
                .byCall(from(ServiceViolatingLayerRules.class, illegalAccessToController)
                        .toMethod(UseCaseTwoController.class, doSomethingTwo)
                        .inLine(13));

        super.services_should_not_access_controllers();
    }

    @Test
    @Override
    public void persistence_should_not_access_services() {
        expectViolation.ofRule("classes that reside in '..persistence..' should " +
                "never access classes that reside in '..service..'")
                .byCall(from(DaoCallingService.class, violateLayerRules)
                        .toMethod(ServiceViolatingLayerRules.class, ServiceViolatingLayerRules.doSomething)
                        .inLine(13));

        super.persistence_should_not_access_services();
    }

    @Test
    @Override
    public void services_should_only_be_accessed_by_controllers_or_other_services() {
        expectViolation.ofRule("classes that reside in '..service..' should " +
                "only be accessed by classes that reside in any package ['..controller..', '..service..']")
                .byCall(from(DaoCallingService.class, violateLayerRules)
                        .toMethod(ServiceViolatingLayerRules.class, ServiceViolatingLayerRules.doSomething)
                        .inLine(13))
                .byCall(from(SomeMediator.class, violateLayerRulesIndirectly)
                        .toMethod(ServiceViolatingLayerRules.class, ServiceViolatingLayerRules.doSomething)
                        .inLine(15));

        super.services_should_only_be_accessed_by_controllers_or_other_services();
    }
}
