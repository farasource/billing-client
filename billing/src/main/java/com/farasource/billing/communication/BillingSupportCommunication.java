package com.farasource.billing.communication;


import com.farasource.billing.util.IabResult;

public interface BillingSupportCommunication {
    void onBillingSupportResult(int response);

    void remoteExceptionHappened(IabResult result);
}
