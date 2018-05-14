package kr.revelope.jenkins.line;

public enum SendType {
	ALWAYS,
	ONLY_SUCCESS,
	ONLY_FAILURE;

	public static SendType getByName(String name) {
		for (SendType sendType : values()) {
			if (sendType.name().equals(name)) {
				return sendType;
			}
		}

		return null;
	}
}
