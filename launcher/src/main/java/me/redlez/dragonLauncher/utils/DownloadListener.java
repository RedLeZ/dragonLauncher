package me.redlez.dragonLauncher.utils;



public interface DownloadListener {
	
	
    void onByteProgress(String fileName, long fileDownloaded, long globalDownloaded, long globalTotal);

    void onProgress(String fileName, long globalDownloaded, long globalTotal);

    void onError(String fileName, Exception e, long globalDownloaded, long globalTotal);
}
