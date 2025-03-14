// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.connection;

import com.intellij.internal.statistic.config.EventLogConfigParserException;
import com.intellij.internal.statistic.config.EventLogExternalSendSettings;
import com.intellij.internal.statistic.config.EventLogExternalSettings;
import com.intellij.internal.statistic.config.bean.EventLogSendConfiguration;
import com.intellij.internal.statistic.config.eventLog.EventLogBuildType;
import com.intellij.internal.statistic.eventLog.EventLogApplicationInfo;
import com.intellij.internal.statistic.eventLog.connection.request.StatsHttpRequests;
import com.intellij.internal.statistic.eventLog.connection.request.StatsResponseException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public abstract class SettingsConnectionService {
  private final @NotNull EventLogApplicationInfo myApplicationInfo;

  private final @NotNull Supplier<EventLogExternalSendSettings> myCachedExternalSettings;

  protected SettingsConnectionService(@NotNull Supplier<@Nullable String> settingsUrlSupplier, @NotNull String recorderId,
                                      @NotNull EventLogApplicationInfo appInfo, long settingsCacheTimeoutMs) {
    myApplicationInfo = appInfo;
    myCachedExternalSettings = new StatisticsCachingSupplier<>(
      () -> {
        final String url = settingsUrlSupplier.get();
        return url != null ? loadSettings(recorderId, url, myApplicationInfo.getProductVersion()) : null;
      },
      settingsCacheTimeoutMs
    );
  }

  protected @Nullable EventLogSendConfiguration getConfiguration(@NotNull EventLogBuildType type) {
    EventLogExternalSendSettings settings = getExternalSettings();
    return settings != null ? settings.getConfiguration(type) : null;
  }

  protected @Nullable String getEndpointValue(@NotNull String attribute) {
    EventLogExternalSendSettings settings = getExternalSettings();
    return settings != null ? settings.getEndpoint(attribute) : null;
  }

  public @NotNull Map<String, String> getOptions() {
    EventLogExternalSendSettings settings = getExternalSettings();
    return settings != null ? settings.getOptions() : Collections.emptyMap();
  }

  protected synchronized @Nullable EventLogExternalSendSettings getExternalSettings() {
    return myCachedExternalSettings.get();
  }

  public @Nullable EventLogExternalSendSettings loadSettings(@NotNull String recorderId, @NotNull String configUrl, @NotNull String appVersion) {
    try {
      return StatsHttpRequests.request(configUrl, myApplicationInfo.getConnectionSettings()).send(r -> {
        try {
          InputStream content = r.read();
          if (content != null) {
            InputStreamReader reader = new InputStreamReader(content, StandardCharsets.UTF_8);
            return EventLogExternalSettings.parseSendSettings(reader, appVersion);
          }
          return null;
        }
        catch (EventLogConfigParserException e) {
          throw new StatsResponseException(e);
        }
      }).getResult();
    }
    catch (StatsResponseException | IOException e) {
      logError(recorderId, e);
    }
    return null;
  }

  private void logError(@NotNull String recorderId, Exception e) {
    final String message = String.format(Locale.ENGLISH, "%s: %s", e.getClass().getName(),
                                         Objects.requireNonNullElse(e.getMessage(), "No message provided"));

    if (e instanceof ConnectException || e instanceof HttpTimeoutException ||
        e instanceof SSLHandshakeException || e instanceof StatsResponseException) {
      // Expected non-critical problems: no connection, bad connection, errors on loading data
      myApplicationInfo.getLogger().info(message);
    } else {
      myApplicationInfo.getLogger().warn(message, e);
    }
    myApplicationInfo.getEventLogger().logLoadingConfigFailed(recorderId, e);
  }
}