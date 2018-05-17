package kr.revelope.jenkins.line;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.http.Consts;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
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
		PrintStream logger = listener.getLogger();

		SendType type = SendType.getByName(sendType);
		if (type == null) {
			logger.println("[LineNotifier][WARN]Invalid Send Type : " + sendType);
			return true;
		}

		Result currentResult = build.getResult();
		if (currentResult == null) {
			logger.println("[LineNotifier][WARN]Build result can not be received.");
			return true;
		}

		LineToken lineToken = getLineToken();
		if (lineToken == null) {
			logger.println("[LineNotifier][WARN]Token is not exist : current token name is " + lineTokenName);
			return true;
		}

		if (type.isSend(currentResult)) {
			send(lineToken.getToken(), createBuildResultMessage(build, currentResult), logger);
			return true;
		}

		if (changeStatus) {
			Run previousBuild = build.getPreviousBuild();
			if (previousBuild == null) {
				logger.println("[LineNotifier][WARN]Previous build result is not exist.");
				return true;
			}

			Result previousResult = previousBuild.getResult();
			if (currentResult != previousResult) {
				logger.println(String.format("[LineNotifier][INFO]Result is change (%s > %s).", previousResult, currentResult));
				send(lineToken.getToken(), createBuildResultMessage(build, currentResult), logger);
			}
		}

		return true;
	}

	private String createBuildResultMessage(AbstractBuild<?, ?> build, Result currentResult) {
		return String.format("[%s][#%s]Build result : %s\n\n%s\n\n%s%s",
				build.getProject().getName(),
				build.getNumber(),
				currentResult.toString(),
				getBuildLog(build, 10),
				build.getProject().getAbsoluteUrl(),
				build.getNumber()
		);
	}

	private String getBuildLog(AbstractBuild<?, ?> build, int maxLine) {
		List<String> logList;
		try {
			logList = build.getLog(maxLine);
		} catch (IOException e) {
			logList = new ArrayList<>();
		}

		StringBuilder logStringBuilder = new StringBuilder();
		for (String logLine : logList) {
			logStringBuilder.append(logLine);
			logStringBuilder.append("\n");
		}

		return logStringBuilder.toString();
	}

	private void send(String token, String message, PrintStream logger) {
		logger.println("[LineNotifier][INFO]Build result send.");
		logger.println(message);
		try {
			Content content = Request.Post("https://notify-api.line.me/api/notify")
					.addHeader("Authorization", "Bearer " + token)
					.bodyString("message=" + message, ContentType.create("application/x-www-form-urlencoded", Consts.UTF_8))
					.execute()
					.returnContent();

			logger.println(String.format("[LineNotifier]Response : %s", content.toString()));
		} catch (Exception e) {
			logger.println("[LineNotifier][WARN]Send error : " + e.getMessage());
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
			List<ListBoxModel.Option> itemList = new ArrayList<>();
			for (LineToken lineToken : getLineTokenList()) {
				itemList.add(new ListBoxModel.Option(lineToken.getName(), lineToken.getName(), StringUtils.equals(lineToken.getName(), value)));
			}

			return new ListBoxModel(itemList);
		}
	}
}
