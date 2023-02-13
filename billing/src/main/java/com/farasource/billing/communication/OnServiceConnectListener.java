package com.farasource.billing.communication;

public interface OnServiceConnectListener {
	void connected();

	void couldNotConnect();
}
