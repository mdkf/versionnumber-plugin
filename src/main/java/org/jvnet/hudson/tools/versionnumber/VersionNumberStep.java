/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jvnet.hudson.tools.versionnumber;

import com.google.inject.Inject;

import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import hudson.Extension;
import hudson.model.Run;
import hudson.EnvVars;

import java.util.Date;

/**
 * Returns the version number according to the
 * specified version number string.
 *
 * Used like:
 *
 * <pre>
 * def x = VersionNumber("${BUILDS_TODAY}")
 * </pre>
 */
public class VersionNumberStep extends AbstractStepImpl {
 
	public final String versionNumberString;

    @DataBoundSetter
    public boolean skipFailedBuilds = false;

    @DataBoundSetter
    public String versionPrefix = null;

    @DataBoundSetter
    public String projectStartDate = null;
	
    @DataBoundConstructor
	public VersionNumberStep(String versionNumberString) {
		if ((versionNumberString == null) || versionNumberString.isEmpty()) {
			throw new IllegalArgumentException("must specify a version number string.");
		}
		this.versionNumberString = versionNumberString;
	}

	public Date getProjectStartDate() {
		Date value = VersionNumberCommon.parseDate(this.projectStartDate);
		if (value.compareTo(new Date(0)) != 0) {
			return value;
		}
		return null;
	}

	public String getVersionPrefix() {
		if ((this.versionPrefix != null) && (!this.versionPrefix.isEmpty())) {
			return this.versionPrefix;
		}
		return null;
	}

    @Extension
	public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "VersionNumber";
        }

        @Override public String getDisplayName() {
            return "Determine the correct version number";
        }

    }

    public static class Execution extends AbstractSynchronousStepExecution<String> {
        
		@StepContextParameter private transient Run run;
		@StepContextParameter private transient EnvVars env;
        @Inject(optional=true) private transient VersionNumberStep step;

        @Override protected String run() throws Exception {
			if (step.versionNumberString != null) {
				try {
					Run prevBuild = VersionNumberCommon.getPreviousBuildWithVersionNumber(run, step.versionPrefix);
					VersionNumberBuildInfo info = VersionNumberCommon.incBuild(run, prevBuild, step.skipFailedBuilds);
					String formattedVersionNumber = VersionNumberCommon.formatVersionNumber(step.versionNumberString,
																step.getProjectStartDate(),
																info,
																env,
																run.getTimestamp());
					// Difference compared to freestyle jobs.
					// If a version prefix is specified, it is forced to be prefixed.
					// Otherwise the version prefix does not function correctly - even in freestyle jobs.
					// In freestlye jobs it is assumed that the user reuses the version prefix
					// within the version number string, but this assumtion is not documented.
					// Hence, it might yield to errors, and therefore in pipeline steps, we 
					// force the version prefix to be prefixed.
					if (step.versionPrefix != null) {
						formattedVersionNumber = step.versionPrefix + formattedVersionNumber;
					}
					run.addAction(new VersionNumberAction(info, formattedVersionNumber));
					return formattedVersionNumber;
				} catch (Exception e) {
				}
			}
			return "";
        }

        private static final long serialVersionUID = 1L;

    }

}