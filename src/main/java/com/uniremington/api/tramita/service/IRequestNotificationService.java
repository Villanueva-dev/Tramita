package com.uniremington.api.tramita.service;

import com.uniremington.api.tramita.model.Request;

public interface IRequestNotificationService {

    void notifyFinalized(Request request);
}