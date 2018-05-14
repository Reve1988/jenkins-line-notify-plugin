package kr.revelope.jenkins.line;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

public class LineToken implements Describable<LineToken>, Serializable {
	private String name;
	private String token;

	@DataBoundConstructor
	public LineToken(String name, String token) {
		this.name = name;
		this.token = token;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	@Override
	public Descriptor<LineToken> getDescriptor() {
		Jenkins instance = Jenkins.getInstanceOrNull();
		if (instance == null) {
			return null;
		}

		return instance.getDescriptorByType(LineToken.DescriptorImpl.class);
	}

	@Extension
	public static final class DescriptorImpl extends Descriptor<LineToken> {
		@Override
		public String getDisplayName() {
			return Messages.LineToken_Descriptor_displayName();
		}

		public FormValidation doCheckName(@QueryParameter("name") final String value) {
			if (StringUtils.isBlank(value)) {
				return FormValidation.error(Messages.LineToken_Descriptor_checkName_blank());
			}

			return FormValidation.ok();
		}

		public FormValidation doCheckToken(@QueryParameter("token") final String value) {
			if (StringUtils.isBlank(value)) {
				return FormValidation.error(Messages.LineToken_Descriptor_checkToken_blank());
			}

			return FormValidation.ok();
		}
	}
}
