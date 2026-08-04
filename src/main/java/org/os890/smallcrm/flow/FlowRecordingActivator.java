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

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.interceptor.Interceptor;
import org.jboss.logging.Logger;
import org.os890.cdi.uml.dynamic.flow.renderer.config.FlowConfig;
import org.os890.cdi.uml.dynamic.flow.renderer.runtime.FlowRuntime;
import org.os890.cdi.uml.dynamic.flow.renderer.sink.FileFlowSink;

/**
 * Switches the cdi-flow recorder on for a running application.
 *
 * <p>The interceptor that {@link FlowRecordingExtension} attached is a pass-through until a
 * runtime is active — cdi-flow activates it from its portable extension, which ArC never runs.
 * Doing it from the startup event has the same effect, and reads the same configuration.
 *
 * <p>It runs before every other startup observer, so a diagram is written for the work the
 * application does while it starts as well, and the file sink is closed again on shutdown.
 */
@ApplicationScoped
public class FlowRecordingActivator {

  private static final Logger LOG = Logger.getLogger(FlowRecordingActivator.class);

  void onStart(@Observes @Priority(Interceptor.Priority.PLATFORM_BEFORE - 100) StartupEvent event) {
    FlowConfig config = FlowConfig.load();
    if (!config.isEnabled()) {
      return;
    }
    FlowRuntime.activate(config, new FileFlowSink(config));
    LOG.infof(
        "cdi-flow is recording; %s diagrams are written to %s",
        config.outputFormat(), config.outputDirectory().toAbsolutePath());
  }

  void onStop(@Observes ShutdownEvent event) {
    FlowRuntime.deactivate();
  }
}
