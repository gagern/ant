/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
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

package org.apache.tools.ant.taskdefs;

import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.tools.ant.AntTypeDefinition;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.ComponentHelper;
import org.apache.tools.ant.Location;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.AntLoaderParameters;
import org.apache.tools.ant.types.LoaderHandler;
import org.apache.tools.ant.types.LoaderHandlerSet;
import org.apache.tools.ant.types.LoaderParameters;
import org.apache.tools.ant.types.LoaderRef;
import org.apache.tools.ant.types.Reference;
import org.apache.tools.ant.types.URLPath;

import sun.misc.Launcher;
import sun.misc.URLClassPath;

/**
 * Create or modifies ClassLoader.
 *
 * The classpath is a regular path.
 *
 * Taskdef and typedef can use the loader you create with the loaderRef attribute.
 *
 * This tasks will not modify the core loader, the project loader
 * or the system loader if "build.sysclasspath=only"
 *
 * The typical use is:
 * <pre>
 *  &lt;path id="ant.deps" &gt;
 *     &lt;fileset dir="myDir" &gt;
 *        &lt;include name="junit.jar, bsf.jar, js.jar, etc"/&gt;
 *     &lt;/fileset&gt;
 *  &lt;/path&gt;
 *
 *  &lt;classloader loader="project" classpathRef="ant.deps" /&gt;
 *
 * </pre>
 *
 * @since Ant 1.7
 */
public class Classloader extends Task {
    /**
     * Actions for ClassLoaderAdapter.
     */
    public static final class Action {
        private static final int IDCREATE = 1;
        private static final int IDAPPEND = 2;
        private static final int IDGETPATH = 3;
        private static final int IDREPORT = 4;
        /**
         * Append Path to an existing ClassLoader instance.
         */
        public static final Action APPEND = new Action(IDAPPEND);
        /**
         * Create a new ClassLoader instance.
         */
        public static final Action CREATE = new Action(IDCREATE);
        /**
         * Get the path of an existing ClassLoader instance.
         */
        public static final Action GETPATH = new Action(IDGETPATH);
        /**
         * Get additional Report information.
         */
        public static final Action REPORT = new Action(IDREPORT);

        private final int value;
        private Action(int value) {
            this.value = value;
        }
    }
    /**
     * ClassLoaderAdapter used to define classloader interaction.
     */
    public static interface ClassLoaderAdapter {
        /**
         * add classloader to the report queue.
         * the adapter should call task.addLoaderToReport to add a loader.
         * @param task the calling Classloader-task.
         * @param classloader the classloader to analyze.
         * @param name the name of the classloader instance.
         * @param loaderStack loaderStack to pass to Classloader.addLoaderToReport.
         * @param loaderNames loaderNames to pass to Classloader.addLoaderToReport.
         */
        void addReportable(
            Classloader task,
            ClassLoader classloader,
            String name,
            Map loaderStack,
            Map loaderNames);
        /**
         * Appends a classpath to an existing classloader instance.
         * @param task the calling Classloader-task.
         * @param classloader the classloader instance to append the path to.
         * @return The ClassLoader instance or null if an error occured.
         */
        boolean appendClasspath(Classloader task, ClassLoader classloader);
        /**
         * Creates a classloader instance.
         * @param task the calling Classloader-task.
         * @return the newly created ClassLoader instance or null if an error occured.
         */
        ClassLoader createClassLoader(Classloader task);
        /**
         * returns the actual classpath of a classloader instance.
         * @param task the calling Classloader-task.
         * @param classloader the classloader instance to get the path from.
         * @param defaultToFile if true, returned url-elements with file protocol
         *         should trim the leading 'file:/' prefix.
         * @return the path or null if an error occured
         */
        String[] getClasspath(
            Classloader task,
            ClassLoader classloader,
            boolean defaultToFile);
        /**
         * Checks whether the adapter supports an action.
         * @param action the action to check.
         * @return true, if action is supported.
         */
        boolean isSupported(Action action);
        /**
         * performs additional reporting.
         * @param to the Reporter Object to report to.
         * @param task the calling Classloader-task.
         * @param classloader the classloader instance to report about.
         * @param name the name of the classloader instance.
         */
        void report(
            Reporter to,
            Classloader task,
            ClassLoader classloader,
            String name);
    }
    /**
     * Mandatory Interface for ClassLoaderParameters.
     */
    public static interface ClassLoaderParameters {
        /**
         * returns the default handler for this descriptor.
         * @return handler.
         */
        LoaderHandler getDefaultHandler();
        /**
         * returns the valuable parameter object which is either the instance itself
         * or the resolved referenced parameters.
         * @return parameters.
         */
        ClassLoaderParameters getParameters();
    }
    /**
     * makes reporting destination transparent for reporting objects.
     */
    public static class Reporter {
        private PrintStream stream;
        private Classloader task;
        Reporter(Classloader task, PrintStream stream) {
            this.task = task;
            this.stream = stream;
        }
        /**
         * writes a message line to the reporting dest.
         * @param s the message line to report.
         */
        public void report(String s) {
            if (stream != null) {
                stream.println(s);
            } else {
                task.log(s, Project.MSG_INFO);
            }
        }
    }

    private URLPath classpath = null;
    private ClassLoaderParameters parameters = null;
    private boolean failOnError;
    private LoaderHandler handler = null;
    private LoaderHandlerSet handlerSet = null;
    private LoaderRef loader = null;
    private String loaderName = null;
    private LoaderRef parentLoader = null;
    private String property = null;
    private boolean report = false;
    private boolean reportPackages = false;
    private boolean reset = false;
    private LoaderRef superLoader = null;
    /**
     * Default constructor
     */
    public Classloader() {
    }
    /**
     * Sets a nested Descriptor element for an AntClassLoader.
     * @param desc the parameters.
     */
    public void addAntParameters(AntLoaderParameters desc) {
        parameters = desc;
    }
    /**
     * sets a nested LoaderHandler element.
     * @param handler the loaderHandler.
     */
    public void addConfiguredHandler(LoaderHandler handler) {
        handler.check();
        if (this.handler != null) {
            throw new BuildException("nested element handler can only specified once");
        }
        this.handler = handler;
    }
    /**
     * sets a nested LoaderHandler element.
     * @param handler the loaderHandler.
     */
    public void setHandler(LoaderHandler handler) {
        handler.check();
        this.handler = handler;
    }
    /**
     * sets a nested ClassLoaderParameters element.
     * @param desc the parameters.
     */
    public void addParameters(LoaderParameters desc) {
        parameters = desc;
    }
    /**
     * sets a nested HandlerSet element.
     * @param handlerSet the handlerSet
     */
    public void addHandlerSet(LoaderHandlerSet handlerSet) {
        if (this.handlerSet != null) {
            throw new BuildException("nested element handlerSet may only specified once");
        }
        this.handlerSet = handlerSet;
    }
    /**
     * sets a HandlerSet ref.
     * @param handlerSet the handlerSet
     */
    public void setHandlerSet(LoaderHandlerSet handlerSet) {
        this.handlerSet = handlerSet;
    }
    /**
     * sets a nested loader element.
     * @param x the loader definition.
     */
    public void addLoader(LoaderRef x) {
        if (x.isStandardLoader(LoaderRef.LoaderSpec.NONE)) {
            throw new BuildException("nested element loader can not be 'none'");
        }
        this.loader = x;
    }
    /**
     * Callback method for ClassLoaderAdapters to add classloaders
     * to the list of loaders to report.
     * @param cl the classloader instance to add.
     * @param name the name of the classloader instance.
     * @param loaderStack a list of loader names by instance.
     * @param loaderNames a list of loader instances by name.
     * @return true, if successfully executed, false otherwise.
     */
    public boolean addLoaderToReport(
        ClassLoader cl,
        String name,
        Map loaderStack,
        Map loaderNames) {
        if (cl == null) {
            Object old = loaderNames.put(name, null);
            if (old != null) {
                throw new BuildException("duplicate classloader name " + name);
            }
        } else {
            Object old = loaderNames.put(name, cl);
            if (old != null) {
                throw new BuildException("duplicate classloader name " + name);
            }
            old = loaderStack.get(cl);
            boolean isNew = (old == null);
            if (old == null) {
                old = new ArrayList();
                loaderStack.put(cl, old);
            }
            ((ArrayList) old).add(name);

            if (isNew) {
                addLoaderToReport(
                    cl.getParent(),
                    name + "->parent",
                    loaderStack,
                    loaderNames);

                LoaderHandlerSet handlerSet = getHandlerSet();
                if (handlerSet == null) {
                    return false;
                }
                LoaderHandler handler =
                    handlerSet.getHandler(this, cl, Action.REPORT);
                if (handler == null) {
                    return false;
                }
                ClassLoaderAdapter adapter = handler.getAdapter(this);
                if (adapter == null) {
                    return false;
                }
                adapter.addReportable(this, cl, name, loaderStack, loaderNames);
            }
        }
        return true;
    }
    /**
     * sets the nested parentLoader element.
     * @param x the parentLoader
     */
    public void addParentLoader(LoaderRef x) {
        this.parentLoader = x;
    }
    /**
     * sets the nested superLoader element.
     * @param x the superLoader.
     */
    public void addSuperLoader(LoaderRef x) {
        this.parentLoader = x;
    }
    /**
     * creates a nested classpath element.
     * @return the classpath.
     */
    public URLPath createClasspath() {
        if (this.classpath == null) {
            this.classpath = new URLPath(getProject());
        }
        return this.classpath.createUrlpath();
    }
    /**
     * do the classloader manipulation.
     */
    public void execute() {
        if (report) {
            executeReport();
            return;
        }
        if (loader == null) {
            throw new BuildException("no loader specified");
        }
        if (!executeCreateModify()) {
            return;
        }
        if (property != null) {
            this.executeProperty();
        }
    }
    private boolean executeCreateModify() {
        URLPath cp = getClasspath();
        ClassLoader cl = null;
        // Are any other references held ? Can we 'close' the loader
        // so it removes the locks on jars ?
        // Can we replace the system classloader by just changing the
        // referenced object?
        // however, is reset really useful?
        if (!reset) {
            cl = loader.getClassLoader(null, false, true);
        }

        boolean create = (cl == null);
        boolean modify = ((cl != null) && (cp != null));
        if (!(create || modify)) {
            return true;
        }

        // Gump friendly - don't mess with the core loader if only classpath
        if ("only".equals(getProject().getProperty("build.sysclasspath"))
         && loader.equalsSysLoader()) {
            log("Changing " + loader.getName() + " is disabled "
                    + "by build.sysclasspath=only",
                Project.MSG_WARN);
            return true;
        }

        if (reset && !loader.isResetPossible()) {
            this.handleError("reseting " + loader.getName() + " is not possible");
            return false;
        }
        if (create && !loader.isResetPossible()) {
            this.handleError("creating " + loader.getName() + " is not possible");
            return false;
        }
        log(
            "handling "
                + this.getLoaderName()
                + ": "
                + ((cl == null) ? "not " : "")
                + "found, cp="
                + this.getClasspath(),
            Project.MSG_DEBUG);
        LoaderHandlerSet handlerSet = null;
        if (cl == null) {
            LoaderHandler handler = getHandler();
            if (handler == null) {
                throw new BuildException("internal error: handler is null");
            }
            ClassLoaderAdapter adapter = handler.getAdapter(this);
            if (adapter == null) {
                return false;
            }
            cl = adapter.createClassLoader(this);
            if (cl == null) {
                return false;
            }
            loader.setClassLoader(cl);
        } else if (cp != null) {
            handlerSet = getHandlerSet();
            if (handlerSet == null) {
                throw new BuildException("internal error: handlerset is null");
            }
            LoaderHandler handler =
                handlerSet.getHandler(this, cl, Action.APPEND);
            if (handler == null) {
                log("NO HANDLER", Project.MSG_DEBUG);
                return false;
            }
            ClassLoaderAdapter adapter = handler.getAdapter(this);
            if (adapter == null) {
                log("NO ADAPTER", Project.MSG_DEBUG);
                return false;
            }
            if (!adapter.appendClasspath(this, cl)) {
                log("NO APPEND", Project.MSG_DEBUG);
                return false;
            }
        }
        return true;
    }
    private boolean executeProperty() {
        ClassLoader cl = loader.getClassLoader(null);
        LoaderHandlerSet handlerSet = getHandlerSet();
        if (handlerSet == null) {
            throw new BuildException("internal error: handlerset is null");
        }
        LoaderHandler handler =
            handlerSet.getHandler(this, cl, Action.GETPATH);
        if (handler == null) {
            return false;
        }
        ClassLoaderAdapter adapter = handler.getAdapter(this);
        if (adapter == null) {
            return false;
        }
        String[] propPath = adapter.getClasspath(this, cl, true);
        if (propPath == null) {
            return false;
        }
        StringBuffer propValue = new StringBuffer();
        if (propPath.length > 0) {
            propValue.append(propPath[0]);
        }
        for (int i = 1; i < propPath.length; i++) {
            propValue.append(';').append(propPath[i]);
        }
        getProject().setProperty(property, propValue.toString());
        return true;
    }
    private String formatIndex(int i) {
        String x = String.valueOf(i + 1);
        if (x.length() == 1) {
            return " " + x;
        }
        return x;
    }
    /**
     * gets the classpath to add to a classloader.
     * @return the classpath.
     */
    public URLPath getClasspath() {
        return classpath;
    }
    /**
     * gets the parameters for a newly created classloader.
     * @return the parameters
     */
    public ClassLoaderParameters getParameters() {
        if (parameters == null) {
            parameters = new LoaderParameters(getProject());
        }
        return parameters;
    }
    /**
     * gets the handler to create a new classloader.
     * @return the handler
     */
    public LoaderHandler getHandler() {
        if (handler == null) {
            handler = getParameters().getDefaultHandler();
        }
        return handler;
    }
    /**
     * gets the handlerset to analyze a given classloader with.
     * @return the handlerset.
     */
    public LoaderHandlerSet getHandlerSet() {
        if (handlerSet == null) {
            handlerSet = new LoaderHandlerSet(getProject());
            handlerSet.addConfiguredHandler(getHandler());
        }
        return handlerSet;
    }
    /**
     * gets the name of the described classloader for logging and report purposes.
     * @return the name.
     */
    public String getLoaderName() {
        if (loaderName == null) {
            loaderName = loader.getName();
        }
        return loaderName;
    }
    /**
     * gets the parent ClassLoader as defined via the parentLoader attribute.
     * @return parent ClassLoader or null if not defined.
     */
    public ClassLoader getParentLoader() {
        if (parentLoader == null) {
            return null;
        }
        return parentLoader.getClassLoader(null, failOnError, false);
    }
    /**
     * gets the super classloader to create a new classloader with.
     * @return the super loader.
     */
    public ClassLoader getSuperLoader() {
        if (superLoader == null) {
            return getClass().getClassLoader();
        }
        return superLoader.getClassLoader(null, failOnError, false);
    }
    /**
     * indicates whether packages should been reported
     * @return true, if packages should been reported, else false.
     */
    public boolean isReportPackages() {
        return reportPackages;
    }
    /**
     * handles an error with respect to the failonerror attribute.
     * @param msg error message.
     */
    public void handleError(String msg) {
        handleError(msg, null, null);
    }
    /**
     * handles an error with respect to the failonerror attribute.
     * @param msg error message.
     * @param ex causing exception.
     */
    public void handleError(String msg, Throwable ex) {
        handleError(msg, ex, null);
    }
    /**
     * handles an error with respect to the failonerror attribute.
     * @param msg error message.
     * @param ex causing exception.
     * @param loc loaction.
     */
    public void handleError(String msg, Throwable ex, Location loc) {
        if (loc == null) {
            loc = this.getLocation();
        }
        if ((msg == null) && (ex != null)) {
            msg = ex.getMessage();
        }
        if (failOnError) {
            throw new BuildException(msg, ex, loc);
        } else {
            log(loc + "Error: " + msg, Project.MSG_ERR);
        }
    }
    /**
     * handle the report.
     */
    protected void executeReport() {
        //let's hope, that no classloader implementation overrides
        // equals/hashCode
        //for 1.4 IdentityHashMap should be used for loaderStack
        HashMap loaderStack = new HashMap();
        HashMap loaderNames = new HashMap();
        boolean addSuccess = true;
        if (!addLoaderToReport(
            ClassLoader.getSystemClassLoader(),
            "1-SystemClassLoader",
            loaderStack,
            loaderNames)) {
            addSuccess = false;
        }
        if (!addLoaderToReport(
            getProject().getClass().getClassLoader(),
            "2-ProjectClassLoader",
            loaderStack,
            loaderNames)) {
            addSuccess = false;
        }
        if (!addLoaderToReport(
            getClass().getClassLoader(),
            "3-CurrentClassLoader",
            loaderStack,
            loaderNames)) {
            addSuccess = false;
        }
        if (!addLoaderToReport(
            Thread.currentThread().getContextClassLoader(),
            "4-ThreadContextClassLoader",
            loaderStack,
            loaderNames)) {
            addSuccess = false;
        }
        if (!addLoaderToReport(
            getProject().getCoreLoader(),
            "5-CoreLoader",
            loaderStack,
            loaderNames)) {
            addSuccess = false;
        }
        String[] rNames =
            (String[]) getProject().getReferences().keySet().toArray(
                new String[getProject().getReferences().size()]);
        Arrays.sort(rNames);
        for (int i = 0; i < rNames.length; i++) {
            Object val = getProject().getReference(rNames[i]);
            if (val instanceof ClassLoader) {
                if (!addLoaderToReport(
                    (ClassLoader) val,
                    "6-id=" + rNames[i],
                    loaderStack,
                    loaderNames)) {
                    addSuccess = false;
                }
            }
        }
        ComponentHelper ch = ComponentHelper.getComponentHelper(getProject());
        Map types = ch.getAntTypeTable();
        rNames = (String[]) types.keySet().toArray(new String[types.size()]);
        Arrays.sort(rNames);
        for (int i = 0; i < rNames.length; i++) {
            AntTypeDefinition val = ch.getDefinition(rNames[i]);
            if (val.getClassLoader() != null) {
                if (!addLoaderToReport(
                    val.getClassLoader(),
                    "7-def=" + rNames[i],
                    loaderStack,
                    loaderNames)) {
                    addSuccess = false;
                }
            }
        }
        rNames = null;
        String[] names =
            (String[]) loaderNames.keySet().toArray(
                new String[loaderNames.size()]);
        Arrays.sort(names);
        for (int i = names.length - 1; i >= 0; i--) {
            Object cl = loaderNames.get(names[i]);
            if (cl != null) {
                loaderStack.put(cl, names[i]);
            }
        }
        //fileoutput and xml-format to be implemented.
        Reporter to = new Reporter(this, null);

        to.report("---------- ClassLoader Report ----------");
        if (!addSuccess) {
            to.report("WARNING: As of missing Loaderhandlers, this report might not be complete.");
        }
        URLClassPath bscp = Launcher.getBootstrapClassPath();
        URL[] urls = bscp.getURLs();
        to.report(" ");
        to.report(" 0. bootstrap classpath: " + urls.length + " elements");
        for (int i = 0; i < urls.length; i++) {
            to.report("         > " + urls[i]);
        }

        for (int i = 0; i < names.length; i++) {
            to.report(" ");
            ClassLoader cl = (ClassLoader) loaderNames.get(names[i]);
            if (cl == null) {
                to.report(
                    formatIndex(i)
                        + ". "
                        + names[i].substring(2)
                        + " is not assigned.");
            } else {
                Object n = loaderStack.get(cl);
                if (names[i].equals(n)) {
                    to.report(formatIndex(i) + ". " + names[i].substring(2));
                    report(to, cl, names[i].substring(2));
                } else {
                    to.report(
                        formatIndex(i)
                            + ". "
                            + names[i].substring(2)
                            + " = "
                            + ((String) n).substring(2)
                            + ". (See above.)");
                }
            }
        }
        to.report("---------- End Of ClassLoader Report ----------");
    }
    /**
     * handle the report for a single classloader
     * @param to Reporter to report
     * @param cl Classloader instance to report
     * @param name name of the classloader instance.
     */
    public void report(Reporter to, ClassLoader cl, String name) {
        to.report("    class: " + cl.getClass().getName());
        LoaderHandlerSet handlerSet = getHandlerSet();
        if (handlerSet == null) {
            throw new BuildException("internal error: handlerset is null");
        }
        LoaderHandler handler = handlerSet.getHandler(this, cl, Action.GETPATH);
        ClassLoaderAdapter adapter;
        if (handler == null) {
            to.report("    path:  - not investigatable (no Loaderhandler found) -");
        } else {
            adapter = handler.getAdapter(this);
            if (adapter == null) {
                to.report("    path:  - not investigatable (Loaderhandler retrieves no adapter) -");
            } else {
                String[] cp = adapter.getClasspath(this, cl, false);
                if (cp == null) {
                    to.report("    path:  - not investigatable (adapter retrieves no path) -");
                } else {
                    to.report("    path:  " + cp.length + " elements");
                    for (int i = 0; i < cp.length; i++) {
                        to.report("         > " + cp[i]);
                    }
                }
            }
        }
        handler = handlerSet.getHandler(this, cl, Action.REPORT);
        if (handler == null) {
            to.report("    - additional parameters not investigatable (no Loaderhandler found) -");
            return;
        }
        adapter = handler.getAdapter(this);
        if (adapter == null) {
            to.report("    - additional parameters not investigatable "
                    + "(Loaderhandler retrieves no adapter) -");
            return;
        }
        adapter.report(to, this, cl, name);
    }

    /**
     * Specify which path will be used. If the loader already exists
     *  the path will be added to the loader.
     * @param classpath an Ant Path object containing the classpath.
     */
    public void setClasspath(URLPath classpath) {
        if (this.classpath == null) {
            this.classpath = classpath;
        } else {
            this.classpath.append(classpath);
        }
    }
    /**
     * Specify which path will be used. If the loader already exists
     *  the path will be added to the loader.
     * @param pathRef reference to a path defined elsewhere
     */
    public void setClasspathRef(Reference pathRef) {
        createClasspath().addReference(pathRef);
    }
    /**
     * sets the failonerror attribute
     * @param onOff value
     */
    public void setFailonerror(boolean onOff) {
        this.failOnError = onOff;
    }
    /**
     * sets the loader attribute
     * @param x the loader
     */
    public void setLoader(LoaderRef x) {
        if (x.isStandardLoader(LoaderRef.LoaderSpec.NONE)) {
            throw new BuildException("attribute loader can not be 'none'");
        }
        this.loader = x;
    }
    /**
     * sets the parameters attribute.
     * @param desc the parameters.
     */
    public void setParameters(LoaderParameters desc) {
        parameters = desc;
    }
    /**
     * sets the parentLoader attribute
     * @param x the parent loader
     */
    public void setParentLoader(LoaderRef x) {
        this.parentLoader = x;
    }
    /**
     * sets the property to put the ClassLoaders path into.
     * @param property name of the property.
     */
    public void setProperty(String property) {
        this.property = property;
    }
    /**
     * sets the report attribute.
     * @param onOff indicates whether to generate a report or not. defaults to false.
     */
    public void setReport(boolean onOff) {
        report = onOff;
    }
    /**
     * sets the reportPackages attribute.
     * @param onOff indicates whether to generate a report or not. defaults to false.
     */
    public void setReportpackages(boolean onOff) {
        reportPackages = onOff;
    }

    /**
     * Reset the classloader, if it already exists. A new loader will
     * be created and all the references to the old one will be removed.
     * (it is not possible to remove paths from a loader). The new
     * path will be used.
     *
     * @param b true if the loader is to be reset.
     */
    public void setReset(boolean b) {
        this.reset = b;
    }
    /**
     * sets the superLoader attribute.
     * @param x the superLoader.
     */
    public void setSuperLoader(LoaderRef x) {
        this.parentLoader = x;
    }
}
