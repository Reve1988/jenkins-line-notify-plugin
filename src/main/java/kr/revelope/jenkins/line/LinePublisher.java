package kr.revelope.jenkins.line;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import net.sf.json.JSONObject;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public class LinePublisher extends Notifier {
	private final String lineTokenName;
	private final String sendType;
	private final boolean changeStatus;

	@DataBoundConstructor
	public LinePublisher(String lineTokenName, String sendType, boolean changeStatus) {
		this.lineTokenName = lineTokenName;
		this.sendType = sendType;
		this.changeStatus = changeStatus;
	}

	public String getLineTokenName() {
		return lineTokenName;
	}

	public String getSendType() {
		return sendType;
	}

	public boolean isChangeStatus() {
		return changeStatus;
	}

	public LineToken getLineToken() {
		DescriptorImpl descriptor = getDescriptor();
		for (LineToken lineToken : descriptor.getLineTokenList()) {
			if (StringUtils.equals(lineTokenName, lineToken.getName())) {
				return lineToken;
			}
		}

		return null;
	}

	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}

	@Override
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		SendType type = SendType.getByName(sendType);
		Result currentResult = build.getResult();
		if (type == null || currentResult == null) {
			listener.getLogger().println(String.format("[LineNotifier][%s]%s\n\n%s", build.getDisplayName(), "Build result can not be received.", build.getBuildStatusUrl()));
			return true;
		}

		switch (type) {
			case ALWAYS:
				listener.getLogger().println("[LineNotifier]Build result send : " + currentResult.toString());
				send(createBuildResultMessage(build, currentResult), listener);
				return true;
			case ONLY_FAILURE:
				if (Result.FAILURE == currentResult) {
					send(createBuildResultMessage(build, currentResult), listener);
					return true;
				}
				break;
			case ONLY_SUCCESS:
				if (Result.SUCCESS == currentResult) {
					listener.getLogger().println("[LineNotifier]Current build is success.");
					send(createBuildResultMessage(build, currentResult), listener);
					return true;
				}
				break;
		}

		Run previousBuild = build.getPreviousBuild();
		if (previousBuild != null) {
			Result previousResult = previousBuild.getResult();
			if (currentResult != previousResult) {
				listener.getLogger().println(String.format("[LineNotifier]Result is change (%s > %s).", previousResult, currentResult));
				send(createBuildResultMessage(build, currentResult), listener);
			}
		}

		return true;
	}

	private String createBuildResultMessage(AbstractBuild<?, ?> build, Result currentResult) {
		String logs = "";
		try {
			StringBuilder logStringBuilder = new StringBuilder();
			for (String logLine : build.getLog(10)) {
				logStringBuilder.append(logLine);
				logStringBuilder.append("\n");
			}

			logs = logStringBuilder.toString();
		} catch (IOException e) {
			// do Nothing.
		}

		return String.format("[%s][#%s]Build result : %s\n\n%s\n\n%s%s",
				build.getProject().getName(),
				build.getNumber(),
				currentResult.toString(),
				logs,
				build.getProject().getAbsoluteUrl(),
				build.getNumber()
		);
	}

	private void send(String message, BuildListener listener) {
		listener.getLogger().println("[LineNotifier]Send build result.");
		try {
			Content content = Request.Post("https://notify-api.line.me/api/notify")
					.addHeader("Authorization", "Bearer " + getLineToken().getToken())
					.bodyForm(Form.form()
							.add("message", message)
							.build()
					).execute()
					.returnContent();

			listener.getLogger().println(String.format("[LineNotifier]Response : %s", content.toString()));
		} catch (Exception e) {
			listener.getLogger().println("[LineNotifier]Send error" + e.getMessage());
		}
	}

	@Extension
	public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
		private List<LineToken> lineTokenList;

		public DescriptorImpl() {
			load();
		}

		public List<LineToken> getLineTokenList() {
			return lineTokenList;
		}

		public void setLineTokenList(List<LineToken> lineTokenList) {
			this.lineTokenList = lineTokenList;
		}

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return Messages.LinePublisher_Descriptor_displayName();
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
			req.bindJSON(this, json.getJSONObject("jenkins-linenotify-plugin"));
			save();

			return super.configure(req, json);
		}

		public List<Descriptor> getLineTokenDescriptorList() {
			Jenkins instance = Jenkins.getInstanceOrNull();
			if (instance == null) {
				return null;
			}

			List<Descriptor> res = new LinkedList<>();
			res.add(instance.getDescriptorByType(LineToken.DescriptorImpl.class));

			return res;
		}

		public ListBoxModel doFillSendTypeItems(@QueryParameter("sendType") final String value) {
			SendType currentSendType = SendType.getByName(value);

			List<ListBoxModel.Option> itemList = new ArrayList<>(SendType.values().length);
			for (SendType sendType : SendType.values()) {
				itemList.add(new ListBoxModel.Option(sendType.name(), sendType.name(), sendType == currentSendType));
			}

			return new ListBoxModel(itemList);
		}

		public ListBoxModel doFillLineTokenNameItems(@QueryParameter("lineTokenName") final String value) {
			List<ListBoxModel.Option> itemList = new ArrayList<>(SendType.values().length);
			for (LineToken lineToken : getLineTokenList()) {
				itemList.add(new ListBoxModel.Option(lineToken.getName(), lineToken.getName(), StringUtils.equals(lineToken.getName(), value)));
			}

			return new ListBoxModel(itemList);
		}
	}
}
