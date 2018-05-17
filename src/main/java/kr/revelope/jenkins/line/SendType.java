package kr.revelope.jenkins.line;

import hudson.model.Result;

public enum SendType {
	NEVER {
		@Override
		public boolean isSend(Result currentResult) {
			return false;
		}
	},
	ALWAYS {
		@Override
		public boolean isSend(Result currentResult) {
			return true;
		}
	},
	ONLY_SUCCESS {
		@Override
		public boolean isSend(Result currentResult) {
			return Result.SUCCESS == currentResult;
		}
	},
	ONLY_FAILURE {
		@Override
		public boolean isSend(Result currentResult) {
			return Result.FAILURE == currentResult;
		}
	};

	public static SendType getByName(String name) {
		for (SendType sendType : values()) {
			if (sendType.name().equals(name)) {
				return sendType;
			}
		}

		return null;
	}

	public abstract boolean isSend(Result currentResult);
}
