/*
 * Copyright 2018 StreamSets Inc.
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
package com.streamsets.pipeline.stage.destination.couchbase;

import com.streamsets.pipeline.api.Config;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.StageUpgrader;
import com.streamsets.pipeline.api.impl.Utils;

import java.util.ArrayList;
import java.util.List;

public class CouchbaseTargetUpgrader implements StageUpgrader {
  @Override
  public List<Config> upgrade(String library, String stageName, String stageInstance, int fromVersion, int toVersion, List<Config> configs) throws StageException {
    switch(fromVersion) {
      case 1:
        upgradeV1ToV2(configs);
        break;
      default:
        throw new IllegalStateException(Utils.format("Unexpected fromVersion {}", fromVersion));
    }
    return configs;
  }

  private void upgradeV1ToV2(List<Config> configs) {
    List<Config> configsToRemove = new ArrayList<>();
    List<Config> configsToAdd = new ArrayList<>();

    for (Config config : configs) {
      switch (config.getName()) {
        case "config.URL":
          configsToAdd.add(new Config("config.couchbase.nodes", config.getValue()));
          configsToRemove.add(config);
          break;
        case "config.bucket":
          configsToAdd.add(new Config("config.couchbase.bucket", config.getValue()));
          configsToRemove.add(config);
          break;
        case "config.bucketPassword":
          configsToAdd.add(new Config("config.credentials.bucketPassword", config.getValue()));
          configsToRemove.add(config);
          break;
        case "config.version":
          if(config.getValue() == "VERSION4") {
            configsToAdd.add(new Config("config.credentials.version", "BUCKET"));
          } else {
            configsToAdd.add(new Config("config.credentials.version", "USER"));
          }
          configsToRemove.add(config);
          break;
        case "config.userName":
          configsToAdd.add(new Config("config.credentials.userName", config.getValue()));
          configsToRemove.add(config);
          break;
        case "config.userPassword":
          configsToAdd.add(new Config("config.credentials.userPassword", config.getValue()));
          configsToRemove.add(config);
          break;
        case "config.generateDocumentKey":
          configsToAdd.add(new Config("config.documentKeyEL", "${uuid:uuid()}"));
          configsToRemove.add(config);
          break;
        case "config.documentKey":
          configsToAdd.add(new Config("config.documentKeyEL", config.getValue()));
          configsToRemove.add(config);
          break;
        default:
          break;
      }
    }
    configs.addAll(configsToAdd);
    configs.removeAll(configsToRemove);
  }
}