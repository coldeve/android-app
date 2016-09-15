package com.kelsos.mbrc.connection_manager;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import com.afollestad.materialdialogs.MaterialDialog;
import com.kelsos.mbrc.R;
import com.kelsos.mbrc.constants.UserInputEventType;
import com.kelsos.mbrc.data.ConnectionSettings;
import com.kelsos.mbrc.events.DefaultSettingsChangedEvent;
import com.kelsos.mbrc.events.MessageEvent;
import com.kelsos.mbrc.events.bus.RxBus;
import com.kelsos.mbrc.events.ui.ConnectionSettingsChanged;
import com.kelsos.mbrc.events.ui.DiscoveryStopped;
import com.kelsos.mbrc.events.ui.NotifyUser;
import com.kelsos.mbrc.ui.activities.FontActivity;
import com.kelsos.mbrc.ui.dialogs.SettingsDialogFragment;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import toothpick.Scope;
import toothpick.Toothpick;
import toothpick.smoothie.module.SmoothieActivityModule;

public class ConnectionManagerActivity extends FontActivity
    implements ConnectionManagerView,
    SettingsDialogFragment.SettingsSaveListener,
    ConnectionAdapter.ConnectionChangeListener {
  @Inject RxBus bus;
  @Inject ConnectionManagerPresenter presenter;
  @BindView(R.id.connection_list) RecyclerView mRecyclerView;
  @BindView(R.id.toolbar) Toolbar mToolbar;
  private MaterialDialog mProgress;
  private Context mContext;
  private ConnectionAdapter adapter;
  private Scope scope;

  @OnClick(R.id.connection_add)
  void onAddButtonClick() {
    SettingsDialogFragment settingsDialog = new SettingsDialogFragment();
    settingsDialog.show(getSupportFragmentManager(), "settings_dialog");
  }

  @OnClick(R.id.connection_scan)
  void onScanButtonClick() {
    MaterialDialog.Builder mBuilder = new MaterialDialog.Builder(mContext);
    mBuilder.title(R.string.progress_scanning);
    mBuilder.content(R.string.progress_scanning_message);
    mBuilder.progress(true, 0);
    mProgress = mBuilder.show();
    bus.post(new MessageEvent(UserInputEventType.StartDiscovery));
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    scope = Toothpick.openScopes(getApplication(), this);
    scope.installModules(new SmoothieActivityModule(this), ConnectionManagerModule.create());
    super.onCreate(savedInstanceState);
    Toothpick.inject(this, scope);
    setContentView(R.layout.ui_activity_connection_manager);
    ButterKnife.bind(this);
    setSupportActionBar(mToolbar);
    mRecyclerView.setHasFixedSize(true);
    RecyclerView.LayoutManager mLayoutManager = new LinearLayoutManager(this);
    mRecyclerView.setLayoutManager(mLayoutManager);
    adapter = new ConnectionAdapter();
    adapter.setChangeListener(this);
    mRecyclerView.setAdapter(adapter);
    presenter.attach(this);
    presenter.load();
  }

  @Override
  protected void onDestroy() {
    Toothpick.closeScope(this);
    super.onDestroy();
  }

  @Override
  protected void onStart() {
    super.onStart();
    mContext = this;

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setTitle(R.string.connection_manager_title);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    presenter.attach(this);
    bus.register(this, ConnectionSettingsChanged.class, this::onConnectionSettingsChange, true);
    bus.register(this, DiscoveryStopped.class, this::onDiscoveryStopped, true);
    bus.register(this, NotifyUser.class, this::onUserNotification, true);
  }

  @Override
  protected void onPause() {
    super.onPause();
    presenter.detach();
    bus.unregister(this);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        onBackPressed();
        break;
      default:
        return false;
    }
    return true;
  }

  @Override
  public void onSave(ConnectionSettings settings) {
    presenter.save(settings);
  }

  private void onConnectionSettingsChange(ConnectionSettingsChanged event) {
    adapter.setSelectionId(event.getDefaultId());
  }

  private void onDiscoveryStopped(DiscoveryStopped event) {

    if (mProgress != null) {
      mProgress.dismiss();
    }

    String message;
    switch (event.getReason()) {
      case NO_WIFI:
        message = getString(R.string.con_man_no_wifi);
        break;
      case NOT_FOUND:
        message = getString(R.string.con_man_not_found);
        break;
      case COMPLETE:
        message = getString(R.string.con_man_success);
        presenter.load();
        break;
      default:
        message = getString(R.string.unknown_reason);
        break;
    }

    Snackbar.make(mRecyclerView, message, Snackbar.LENGTH_SHORT).show();
  }

  private void onUserNotification(NotifyUser event) {
    final String message = event.isFromResource() ? getString(event.getResId()) : event.getMessage();

    Snackbar.make(mRecyclerView, message, Snackbar.LENGTH_SHORT).show();
  }

  @Override
  public void onDelete(ConnectionSettings settings) {
    presenter.delete(settings);
  }

  @Override
  public void onEdit(ConnectionSettings settings) {
    SettingsDialogFragment settingsDialog = SettingsDialogFragment.newInstance(settings);
    FragmentManager fragmentManager = getSupportFragmentManager();
    settingsDialog.show(fragmentManager, "settings_dialog");
  }

  @Override
  public void onDefault(ConnectionSettings settings) {
    presenter.setDefault(settings);
  }

  @Override
  public void updateModel(ConnectionModel connectionModel) {
    adapter.update(connectionModel);
  }

  @Override
  public void defaultChanged() {
    bus.post(DefaultSettingsChangedEvent.create());
  }

  @Override
  public void dataUpdated() {
    presenter.load();
  }
}