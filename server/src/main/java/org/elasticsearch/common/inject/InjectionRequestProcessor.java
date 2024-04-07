/*
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.elasticsearch.common.inject;

import org.elasticsearch.common.inject.internal.Errors;
import org.elasticsearch.common.inject.internal.ErrorsException;
import org.elasticsearch.common.inject.internal.InternalContext;
import org.elasticsearch.common.inject.spi.InjectionPoint;
import org.elasticsearch.common.inject.spi.InjectionRequest;
import org.elasticsearch.common.inject.spi.StaticInjectionRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Handles {@link Binder#requestInjection} and {@link Binder#requestStaticInjection} commands.
 *
 * @author crazybob@google.com (Bob Lee)
 * @author jessewilson@google.com (Jesse Wilson)
 * @author mikeward@google.com (Mike Ward)
 */
class InjectionRequestProcessor extends AbstractProcessor {

    private final List<StaticInjectionProvider> staticInjectionProviders = new ArrayList<>();
    private final Initializer initializer;

    InjectionRequestProcessor(Errors errors, Initializer initializer) {
        super(errors);
        this.initializer = initializer;
    }

    @Override
    public Boolean visit(StaticInjectionRequest request) {
        staticInjectionProviders.add(new StaticInjectionProviderImpl(injector, request));
        return true;
    }

    @Override
    public Boolean visit(InjectionRequest<?> request) {
        Set<InjectionPoint> injectionPoints;
        try {
            injectionPoints = request.getInjectionPoints();
        } catch (ConfigurationException e) {
            errors.merge(e.getErrorMessages());
            injectionPoints = e.getPartialValue();
        }

        initializer.requestInjection(injector, request.getInstance(), request.getSource(), injectionPoints);
        return true;
    }

    public void validate() {
        for (StaticInjectionProvider provider : staticInjectionProviders) {
            provider.validate();
        }
    }

    public void injectMembers() {
        for (StaticInjectionProvider provider : staticInjectionProviders) {
            provider.injectMembers();
        }
    }
}

interface StaticInjectionProvider {
    void validate();
    void injectMembers();
}
class StaticInjectionProviderImpl implements StaticInjectionProvider {
    private final InjectorImpl injector;
    private final Object source;
    private final StaticInjectionRequest request;
    private List<SingleMemberInjector> memberInjectors;

    StaticInjectionProviderImpl(InjectorImpl injector, StaticInjectionRequest request) {
        this.injector = injector;
        this.source = request.getSource();
        this.request = request;
    }

    @Override
    public void validate() {
        Errors errorsForMember = errors.withSource(source);
        Set<InjectionPoint> injectionPoints;
        try {
            injectionPoints = request.getInjectionPoints();
        } catch (ConfigurationException e) {
            errors.merge(e.getErrorMessages());
            injectionPoints = e.getPartialValue();
        }
        memberInjectors = injector.membersInjectorStore.getInjectors(injectionPoints, errorsForMember);
    }

    @Override
    public void injectMembers() {
        try {
            injector.callInContext(new ContextualCallable<Void>() {
                @Override
                public Void call(InternalContext context) {
                    for (SingleMemberInjector injector : memberInjectors) {
                        injector.inject(errors, context, null);
                    }
                    return null;
                }
            });
        } catch (ErrorsException e) {
            throw new AssertionError();
        }
    }
}


