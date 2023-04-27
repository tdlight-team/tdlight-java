package it.tdlight.client;

import it.tdlight.utils.LibraryVersion;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

@SuppressWarnings("unused")
public final class TDLibSettings {

	private static final Path USER_HOME_PATH = Paths.get(System.getProperty("user.home"));
	private static final String DISPLAY_LANGUAGE = Locale.getDefault().getDisplayLanguage();
	private static final String OS_NAME = System.getProperty("os.name", "unknown");
	private static final String OS_VERSION = System.getProperty("os.version", "unknown");
	private static final Path TDLIGHT_SESSION_PATH = USER_HOME_PATH.resolve("tdlight-session");
	private static final Path TDLIGHT_SESSION_DATA_PATH = TDLIGHT_SESSION_PATH.resolve("data");
	private static final Path TDLIGHT_SESSION_DOWNLOADS_PATH = TDLIGHT_SESSION_PATH.resolve("downloads");

	private boolean useTestDatacenter;
	private Path databaseDirectoryPath;
	private Path downloadedFilesDirectoryPath;
	private boolean fileDatabaseEnabled;
	private boolean chatInfoDatabaseEnabled;
	private boolean messageDatabaseEnabled;
	private APIToken apiToken;
	private String systemLanguageCode;
	private String deviceModel;
	private String systemVersion;
	private String applicationVersion;
	private boolean enableStorageOptimizer;
	private boolean ignoreFileNames;

	private TDLibSettings(boolean useTestDatacenter,
			Path databaseDirectoryPath,
			Path downloadedFilesDirectoryPath,
			boolean fileDatabaseEnabled,
			boolean chatInfoDatabaseEnabled,
			boolean messageDatabaseEnabled,
			APIToken apiToken,
			String systemLanguageCode,
			String deviceModel,
			String systemVersion,
			String applicationVersion,
			boolean enableStorageOptimizer,
			boolean ignoreFileNames) {
		this.useTestDatacenter = useTestDatacenter;
		this.databaseDirectoryPath = databaseDirectoryPath;
		this.downloadedFilesDirectoryPath = downloadedFilesDirectoryPath;
		this.fileDatabaseEnabled = fileDatabaseEnabled;
		this.chatInfoDatabaseEnabled = chatInfoDatabaseEnabled;
		this.messageDatabaseEnabled = messageDatabaseEnabled;
		this.apiToken = apiToken;
		this.systemLanguageCode = systemLanguageCode;
		this.deviceModel = deviceModel;
		this.systemVersion = systemVersion;
		this.applicationVersion = applicationVersion;
		this.enableStorageOptimizer = enableStorageOptimizer;
		this.ignoreFileNames = ignoreFileNames;
	}

	public static TDLibSettings create(APIToken apiToken) {
		return new TDLibSettings(false,
				TDLIGHT_SESSION_DATA_PATH,
				TDLIGHT_SESSION_DOWNLOADS_PATH,
				true,
				true,
				true,
				apiToken,
				DISPLAY_LANGUAGE,
				OS_NAME,
				OS_VERSION,
				LibraryVersion.VERSION,
				true,
				false
		);
	}

	public boolean isUsingTestDatacenter() {
		return useTestDatacenter;
	}

	public void setUseTestDatacenter(boolean useTestDatacenter) {
		this.useTestDatacenter = useTestDatacenter;
	}

	public Path getDatabaseDirectoryPath() {
		return databaseDirectoryPath;
	}

	public void setDatabaseDirectoryPath(Path databaseDirectoryPath) {
		this.databaseDirectoryPath = databaseDirectoryPath;
	}

	public Path getDownloadedFilesDirectoryPath() {
		return downloadedFilesDirectoryPath;
	}

	public void setDownloadedFilesDirectoryPath(Path downloadedFilesDirectoryPath) {
		this.downloadedFilesDirectoryPath = downloadedFilesDirectoryPath;
	}

	public boolean isFileDatabaseEnabled() {
		return fileDatabaseEnabled;
	}

	public void setFileDatabaseEnabled(boolean fileDatabaseEnabled) {
		this.fileDatabaseEnabled = fileDatabaseEnabled;
	}

	public boolean isChatInfoDatabaseEnabled() {
		return chatInfoDatabaseEnabled;
	}

	public void setChatInfoDatabaseEnabled(boolean chatInfoDatabaseEnabled) {
		this.chatInfoDatabaseEnabled = chatInfoDatabaseEnabled;
	}

	public boolean isMessageDatabaseEnabled() {
		return messageDatabaseEnabled;
	}

	public void setMessageDatabaseEnabled(boolean messageDatabaseEnabled) {
		this.messageDatabaseEnabled = messageDatabaseEnabled;
	}

	public APIToken getApiToken() {
		return apiToken;
	}

	public void setApiToken(APIToken apiToken) {
		this.apiToken = apiToken;
	}

	public String getSystemLanguageCode() {
		return systemLanguageCode;
	}

	public void setSystemLanguageCode(String systemLanguageCode) {
		this.systemLanguageCode = systemLanguageCode;
	}

	public String getDeviceModel() {
		return deviceModel;
	}

	public void setDeviceModel(String deviceModel) {
		this.deviceModel = deviceModel;
	}

	public String getSystemVersion() {
		return systemVersion;
	}

	public void setSystemVersion(String systemVersion) {
		this.systemVersion = systemVersion;
	}

	public String getApplicationVersion() {
		return applicationVersion;
	}

	public void setApplicationVersion(String applicationVersion) {
		this.applicationVersion = applicationVersion;
	}

	public boolean isStorageOptimizerEnabled() {
		return enableStorageOptimizer;
	}

	public void setEnableStorageOptimizer(boolean enableStorageOptimizer) {
		this.enableStorageOptimizer = enableStorageOptimizer;
	}

	public boolean isIgnoreFileNames() {
		return ignoreFileNames;
	}

	public void setIgnoreFileNames(boolean ignoreFileNames) {
		this.ignoreFileNames = ignoreFileNames;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		TDLibSettings that = (TDLibSettings) o;
		return useTestDatacenter == that.useTestDatacenter && fileDatabaseEnabled == that.fileDatabaseEnabled
				&& chatInfoDatabaseEnabled == that.chatInfoDatabaseEnabled
				&& messageDatabaseEnabled == that.messageDatabaseEnabled
				&& enableStorageOptimizer == that.enableStorageOptimizer && ignoreFileNames == that.ignoreFileNames
				&& Objects.equals(databaseDirectoryPath, that.databaseDirectoryPath) && Objects.equals(
				downloadedFilesDirectoryPath,
				that.downloadedFilesDirectoryPath
		) && Objects.equals(apiToken, that.apiToken) && Objects.equals(systemLanguageCode, that.systemLanguageCode)
				&& Objects.equals(deviceModel, that.deviceModel) && Objects.equals(systemVersion, that.systemVersion)
				&& Objects.equals(applicationVersion, that.applicationVersion);
	}

	@Override
	public int hashCode() {
		return Objects.hash(useTestDatacenter,
				databaseDirectoryPath,
				downloadedFilesDirectoryPath,
				fileDatabaseEnabled,
				chatInfoDatabaseEnabled,
				messageDatabaseEnabled,
				apiToken,
				systemLanguageCode,
				deviceModel,
				systemVersion,
				applicationVersion,
				enableStorageOptimizer,
				ignoreFileNames
		);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", TDLibSettings.class.getSimpleName() + "[", "]")
				.add("useTestDatacenter=" + useTestDatacenter)
				.add("databaseDirectoryPath=" + databaseDirectoryPath)
				.add("downloadedFilesDirectoryPath=" + downloadedFilesDirectoryPath)
				.add("fileDatabaseEnabled=" + fileDatabaseEnabled)
				.add("chatInfoDatabaseEnabled=" + chatInfoDatabaseEnabled)
				.add("messageDatabaseEnabled=" + messageDatabaseEnabled)
				.add("apiData=" + apiToken)
				.add("systemLanguageCode='" + systemLanguageCode + "'")
				.add("deviceModel='" + deviceModel + "'")
				.add("systemVersion='" + systemVersion + "'")
				.add("applicationVersion='" + applicationVersion + "'")
				.add("enableStorageOptimizer=" + enableStorageOptimizer)
				.add("ignoreFileNames=" + ignoreFileNames)
				.toString();
	}
}
