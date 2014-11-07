/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.lib.basics.log;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.ErrorId;
import com.streamsets.pipeline.api.Field;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageDef;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.api.base.BaseSource;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

@StageDef(name = "tailLog",
          version="1.0.1",
          label="Tail log files")
public class TailLogSource extends BaseSource {

  private static final int SLEEP_TIME_WAITING_FOR_BATCH_SIZE_MS = 100;

  private static final String OFFSET = "tailing";

  @ConfigDef(name="logFile",
             required = true,
             type = ConfigDef.Type.STRING,
             label = "Log file")
  public String logFileName;

  @ConfigDef(name="maxLinesPrefetch",
             required = false,
             type = ConfigDef.Type.INTEGER,
             label = "Maximum lines prefetch",
             defaultValue = "100")
  public int maxLinesPrefetch;

  @ConfigDef(name="batchSize",
             required = false,
             type = ConfigDef.Type.INTEGER,
             label = "Batch size",
             defaultValue = "10")
  public int batchSize;

  @ConfigDef(name="maxWaitTime",
             required = false,
             type = ConfigDef.Type.INTEGER,
             label = "Maximum wait time to fill up a batch",
             defaultValue = "5000")
  public int maxWaitTime;

  @ConfigDef(name="logLineRecordFieldName",
             required = false,
             type = ConfigDef.Type.STRING,
             label = "Name of the field with the log line in the record",
             defaultValue = "logLine")
  public String logLineRecordFieldName;

  public enum ERROR implements ErrorId {
    NO_PERMISSION_TO_READ_LOG_FILE("Insufficient permissions to read the log file '{}'"),
    ;

    private final String template;

    ERROR(String template) {
      this.template = template;
    }

    @Override
    public String getMessageTemplate() {
      return template;
    }
  }

  private BlockingQueue<String> logLinesQueue;
  private TailLog tailLog;

  @Override
  protected void init() throws StageException {
    super.init();
    File logFile = new File(logFileName);
    if (logFile.exists() && !logFile.canRead()) {
      throw new StageException(ERROR.NO_PERMISSION_TO_READ_LOG_FILE, logFile);
    }
    logLinesQueue = new ArrayBlockingQueue<String>(maxLinesPrefetch);
    tailLog = new TailLog(logFile, getInfo(), logLinesQueue);
    tailLog.start();
  }

  @Override
  public void destroy() {
    tailLog.stop();
    super.destroy();
  }

  @Override
  public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
    long start = System.currentTimeMillis();
    int fetch = Math.min(batchSize, maxBatchSize);
    String now = "." + Long.toString(System.currentTimeMillis()) + ".";
    List<String> lines = new ArrayList<String>(fetch);
    while (((System.currentTimeMillis() - start) < maxWaitTime) && (logLinesQueue.size() < fetch)) {
      try {
        Thread.sleep(SLEEP_TIME_WAITING_FOR_BATCH_SIZE_MS);
      } catch (InterruptedException ex) {
        break;
      }
    }
    logLinesQueue.drainTo(lines, fetch);
    for (int i = 0; i < lines.size(); i++) {
      Record record = getContext().createRecord(logFileName + now + i);
      record.setField(logLineRecordFieldName, Field.create(lines.get(i)));
      batchMaker.addRecord(record);
    }
    return OFFSET;
  }

}
