/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.jsongen.generator.bean;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.List;

class BeanDefinition {
    MethodElement creator;
    List<Property> creatorProps;

    List<Property> props;

    static class Property {
        final String name;

        @Nullable
        FieldElement field = null;
        @Nullable
        MethodElement getter = null;
        @Nullable
        MethodElement setter = null;
        @Nullable
        ParameterElement creatorParameter = null;

        Property(String name) {
            this.name = name;
        }
    }
}
