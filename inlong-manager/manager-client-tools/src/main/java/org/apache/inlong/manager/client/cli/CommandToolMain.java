/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.manager.client.cli;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CommandToolMain {

    protected final Map<String, Class<?>> commandMap = new HashMap<>();
    private final JCommander jcommander;

    @Parameter(names = {"-h", "--help"}, help = true, description = "Get all command about managerctl.")
    boolean help;

    CommandToolMain() {
        jcommander = new JCommander();
        jcommander.setProgramName("managerctl");
        jcommander.addObject(this);

        commandMap.put("list", CommandList.class);
        commandMap.put("describe", CommandDescribe.class);
        commandMap.put("create", CommandCreate.class);

        for (Map.Entry<String, Class<?>> cmd : commandMap.entrySet()) {
            try {
                jcommander.addCommand(cmd.getKey(), cmd.getValue().getConstructor().newInstance());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        CommandToolMain inlongAdminTool = new CommandToolMain();
        if (inlongAdminTool.run(args)) {
            System.exit(0);
        } else {
            System.exit(1);
        }
    }

    boolean run(String[] args) {
        try {
            jcommander.parse(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            jcommander.usage();
            return false;
        }

        if (help || args.length == 0) {
            jcommander.usage();
            return true;
        }

        String cmd = args[0];
        JCommander obj = jcommander.getCommands().get(cmd);
        CommandBase cmdObj = (CommandBase) obj.getObjects().get(0);
        return cmdObj.run(Arrays.copyOfRange(args, 1, args.length));
    }
}
