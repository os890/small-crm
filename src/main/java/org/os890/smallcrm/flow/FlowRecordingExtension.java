/*
 * Copyright 2026 the Small CRM authors.
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

package org.os890.smallcrm.flow;

import jakarta.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import jakarta.enterprise.inject.build.compatible.spi.ClassConfig;
import jakarta.enterprise.inject.build.compatible.spi.Enhancement;
import jakarta.enterprise.lang.model.AnnotationInfo;
import jakarta.enterprise.lang.model.declarations.ClassInfo;
import jakarta.enterprise.lang.model.declarations.MethodInfo;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.os890.cdi.uml.dynamic.flow.renderer.api.FlowRecorded;
import org.os890.cdi.uml.dynamic.flow.renderer.config.FlowConfig;

/**
 * Puts the cdi-flow recording interceptor on this application's beans while the application is
 * built.
 *
 * <p>cdi-flow ships a portable CDI extension, which is the wrong shape for this application: ArC
 * resolves beans, interceptors and bindings during the Quarkus build and never runs a
 * {@code jakarta.enterprise.inject.spi.Extension}. Its interceptor and its
 * {@link FlowRecorded @FlowRecorded} binding are ordinary CDI artefacts though, so all that is
 * missing is something to attach the binding — which is what this build compatible extension
 * does, at the one moment ArC still accepts an annotation.
 *
 * <p>Nothing is instrumented unless {@code cdi-flow.enabled=true} is set <em>for the build</em>
 * (the recording run passes {@code -Dcdi-flow.enabled=true}). A normal build leaves every bean
 * exactly as written, so an unconfigured application carries no interceptor and no runtime cost.
 * {@code cdi-flow.include-pattern} and {@code cdi-flow.exclude-pattern} narrow the selection
 * further, and never widen it: only this application's own beans are ever candidates.
 */
public class FlowRecordingExtension implements BuildCompatibleExtension {

  private static final Logger LOG = Logger.getLogger(FlowRecordingExtension.class.getName());

  /** Only this application's own beans are recorded; the framework's own are noise. */
  private static final String APPLICATION_PACKAGE = "org.os890.smallcrm.";

  /** The recording plumbing itself must stay out of the diagrams. */
  private static final String OWN_PACKAGE = "org.os890.smallcrm.flow.";

  /**
   * A bean the container cannot subclass cannot be intercepted either, and adding the binding
   * anyway would turn a working application into a failed build.
   */
  private static final Set<String> NON_BEAN_ANNOTATIONS =
      Set.of(
          "jakarta.persistence.Entity",
          "jakarta.persistence.Embeddable",
          "jakarta.persistence.MappedSuperclass",
          "jakarta.interceptor.Interceptor",
          "jakarta.decorator.Decorator",
          "jakarta.ws.rs.ext.Provider");

  private final FlowConfig config = FlowConfig.load();

  private int instrumented;

  @Enhancement(types = Object.class, withSubtypes = true)
  public void applyRecordingBinding(ClassConfig candidate) {
    if (!config.isEnabled()) {
      return;
    }
    ClassInfo type = candidate.info();
    if (!isRecordable(type)) {
      return;
    }
    candidate.addAnnotation(FlowRecorded.class);
    instrumented++;
    LOG.fine(() -> "cdi-flow records " + type.name());
    if (instrumented == 1) {
      LOG.info("cdi-flow is recording the beans of " + APPLICATION_PACKAGE + "*");
    }
  }

  private boolean isRecordable(ClassInfo type) {
    String name = type.name();
    if (!name.startsWith(APPLICATION_PACKAGE) || name.startsWith(OWN_PACKAGE)) {
      return false;
    }
    // An inner class is not proxyable, and an anonymous or generated one is not a bean at all.
    if (name.indexOf('$') >= 0) {
      return false;
    }
    // Records, enums, interfaces and annotations are either not beans or not subclassable; a
    // final class and a final business method are what would actually break the build.
    if (!type.isPlainClass() || type.isAbstract() || type.isFinal()) {
      return false;
    }
    if (type.methods().stream().anyMatch(FlowRecordingExtension::blocksSubclassing)) {
      return false;
    }
    if (!hasUsableConstructor(type)) {
      return false;
    }
    Set<String> annotationNames =
        type.annotations().stream()
            .map(FlowRecordingExtension::annotationName)
            .collect(Collectors.toSet());
    if (annotationNames.stream().anyMatch(NON_BEAN_ANNOTATIONS::contains)) {
      return false;
    }
    // The include/exclude patterns of cdi-flow, applied within the application's own beans.
    return config.matches(name, annotationNames);
  }

  private static boolean blocksSubclassing(MethodInfo method) {
    return method.isFinal() && !method.isStatic() && !method.isConstructor();
  }

  /**
   * The interceptor subclass calls a constructor without arguments, so a bean needs one — either
   * declared, or implicit because it declares no constructor at all.
   */
  private static boolean hasUsableConstructor(ClassInfo type) {
    return type.constructors().isEmpty()
        || type.constructors().stream().anyMatch(constructor -> constructor.parameters().isEmpty());
  }

  private static String annotationName(AnnotationInfo annotation) {
    return annotation.declaration().name();
  }
}
