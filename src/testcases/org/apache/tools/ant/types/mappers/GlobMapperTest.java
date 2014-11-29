/*
 * Copyright  2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.tools.ant.types.mappers;

import org.apache.tools.ant.BuildFileTest;

/**
 * Testcase for the &lt;globmapper&gt; mapper.
 *
 */
public class GlobMapperTest extends BuildFileTest {
    public GlobMapperTest(String name) {
        super(name);
    }
    public void setUp() {
        configureProject("src/etc/testcases/types/mappers/globmapper.xml");
    }

    public void testIgnoreCase() {
        executeTarget("ignore.case");
    }
    public void testHandleDirChar() {
        executeTarget("handle.dirchar");
    }
}


