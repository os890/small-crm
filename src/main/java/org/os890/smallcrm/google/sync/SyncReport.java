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

package org.os890.smallcrm.google.sync;

/**
 * What one pass over one resource did.
 *
 * <p>Counted rather than merely logged, because "it says it synced" is not the same as "anything
 * happened", and the difference is what a user needs when they are wondering why their contact
 * is not there.
 *
 * @param pulledIn records created here from Google
 * @param pulledUpdated records here brought up to date from Google
 * @param pulledDeleted records here removed because Google no longer has them
 * @param pushedNew records created in Google from here
 * @param pushedUpdated records in Google brought up to date from here
 * @param readOnly records left alone because Google holds something this CRM cannot represent
 * @param skipped records neither side could reconcile, which is worth showing rather than hiding
 */
public record SyncReport(
    String resource,
    int pulledIn,
    int pulledUpdated,
    int pulledDeleted,
    int pushedNew,
    int pushedUpdated,
    int readOnly,
    int skipped,
    String error) {

  public static SyncReport failed(String resource, String error) {
    return new SyncReport(resource, 0, 0, 0, 0, 0, 0, 0, error);
  }

  public boolean changedAnything() {
    return pulledIn + pulledUpdated + pulledDeleted + pushedNew + pushedUpdated > 0;
  }

  /** Accumulates one resource's counters while a pass runs. */
  public static final class Counter {
    private final String resource;
    private int pulledIn;
    private int pulledUpdated;
    private int pulledDeleted;
    private int pushedNew;
    private int pushedUpdated;
    private int readOnly;
    private int skipped;

    public Counter(String resource) {
      this.resource = resource;
    }

    public void pulledIn() {
      pulledIn++;
    }

    public void pulledUpdated() {
      pulledUpdated++;
    }

    public void pulledDeleted() {
      pulledDeleted++;
    }

    public void pushedNew() {
      pushedNew++;
    }

    public void pushedUpdated() {
      pushedUpdated++;
    }

    public void readOnly() {
      readOnly++;
    }

    public void skipped() {
      skipped++;
    }

    public SyncReport done() {
      return new SyncReport(
          resource, pulledIn, pulledUpdated, pulledDeleted, pushedNew, pushedUpdated, readOnly,
          skipped, null);
    }
  }
}
