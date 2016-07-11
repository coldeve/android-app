package com.kelsos.mbrc.controller;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.kelsos.mbrc.configuration.CommandRegistration;
import com.kelsos.mbrc.constants.UserInputEventType;
import com.kelsos.mbrc.events.MessageEvent;
import com.kelsos.mbrc.messaging.NotificationService;
import com.kelsos.mbrc.model.MainDataModel;
import com.kelsos.mbrc.services.BrowseSync;
import com.kelsos.mbrc.services.ProtocolHandler;
import com.kelsos.mbrc.services.ServiceDiscovery;
import com.kelsos.mbrc.services.SocketService;
import com.kelsos.mbrc.utilities.RemoteBroadcastReceiver;
import com.raizlabs.android.dbflow.config.FlowConfig;
import com.raizlabs.android.dbflow.config.FlowManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import roboguice.RoboGuice;
import timber.log.Timber;

@Singleton public class RemoteService extends Service {

  private final IBinder mBinder = new ControllerBinder();
  @Inject
  private RemoteController remoteController;

  @Inject private MainDataModel mainDataModel;
  @Inject private ProtocolHandler protocolHandler;
  @Inject private SocketService socketService;
  @Inject private ServiceDiscovery discovery;
  @Inject private RemoteBroadcastReceiver receiver;
  @Inject private NotificationService notificationService;
  @Inject private BrowseSync browseSync;

  private ExecutorService threadPoolExecutor;

  public RemoteService() { }

  @Override public IBinder onBind(Intent intent) {
    return mBinder;
  }

  @Override public void onCreate() {
    super.onCreate();
    RoboGuice.getInjector(this).injectMembers(this);
    FlowManager.init(new FlowConfig.Builder(this).openDatabasesOnInit(true).build());
    this.registerReceiver(receiver, receiver.filter());

  }

  /**
   * Takes a MessageEvent and passes it to the command execution function.
   *
   * @param event The message received.
   */
  public void handleUserActionEvents(MessageEvent event) {
    remoteController.handleUserActionEvents(event);
  }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    Timber.d("Background Service::Started");
    CommandRegistration.register(remoteController);
    threadPoolExecutor = Executors.newSingleThreadExecutor();
    threadPoolExecutor.execute(remoteController);
    remoteController.executeCommand(new MessageEvent(UserInputEventType.StartConnection));
    discovery.startDiscovery(() -> browseSync.sync());

    return super.onStartCommand(intent, flags, startId);
  }

  @Override public void onDestroy() {
    remoteController.executeCommand(new MessageEvent(UserInputEventType.CancelNotification));
    remoteController.executeCommand(new MessageEvent(UserInputEventType.TerminateConnection));
    CommandRegistration.unregister(remoteController);
    if (threadPoolExecutor != null) {
      threadPoolExecutor.shutdownNow();
    }
    Timber.d("Background Service::Destroyed");
    this.unregisterReceiver(receiver);
    RoboGuice.destroyInjector(this);
    super.onDestroy();
  }

  private class ControllerBinder extends Binder {
    @SuppressWarnings("unused") ControllerBinder getService() {
      return ControllerBinder.this;
    }
  }
}