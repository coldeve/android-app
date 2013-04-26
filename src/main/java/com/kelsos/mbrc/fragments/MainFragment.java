package com.kelsos.mbrc.fragments;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.*;
import android.widget.*;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.github.rtyley.android.sherlock.roboguice.fragment.RoboSherlockFragment;
import com.google.inject.Inject;
import com.kelsos.mbrc.R;
import com.kelsos.mbrc.controller.ActiveFragmentProvider;
import com.kelsos.mbrc.data.UserAction;
import com.kelsos.mbrc.enums.ConnectionStatus;
import com.kelsos.mbrc.enums.PlayState;
import com.kelsos.mbrc.events.ProtocolEvent;
import com.kelsos.mbrc.events.UserInputEvent;
import com.kelsos.mbrc.events.MessageEvent;
import com.kelsos.mbrc.others.Const;
import com.kelsos.mbrc.others.Protocol;
import com.squareup.otto.Bus;
import roboguice.inject.InjectView;

import java.util.Timer;
import java.util.TimerTask;

public class MainFragment extends RoboSherlockFragment
{
	// Inject elements of the view
	@InjectView(R.id.main_artist_label)
	TextView artistLabel;
	@InjectView(R.id.main_title_label)
	TextView titleLabel;
	@InjectView(R.id.main_label_album)
	TextView albumLabel;
	@InjectView(R.id.main_label_year)
	TextView yearLabel;
	@InjectView(R.id.main_track_progress_current)
	TextView trackProgressCurrent;
	@InjectView(R.id.main_track_duration_total)
	TextView trackDuration;
	@InjectView(R.id.main_button_play_pause)
	ImageButton playPauseButton;
	@InjectView(R.id.main_button_previous)
	ImageButton previousButton;
	@InjectView(R.id.main_button_next)
	ImageButton nextButton;
	@InjectView(R.id.main_volume_seeker)
	SeekBar volumeSlider;
	@InjectView(R.id.main_track_progress_seeker)
	SeekBar trackProgressSlider;
	@InjectView(R.id.main_button_stop)
	ImageButton stopButton;
	@InjectView(R.id.main_mute_button)
	ImageButton muteButton;
	@InjectView(R.id.main_last_fm_button)
	ImageButton scrobbleButton;
	@InjectView(R.id.main_shuffle_button)
	ImageButton shuffleButton;
	@InjectView(R.id.main_repeat_button)
	ImageButton repeatButton;
	@InjectView(R.id.main_button_connect)
	ImageButton connectivityIndicator;
	@InjectView(R.id.main_album_cover_image_view)
	ImageView albumCover;
    @InjectView(R.id.ratingWrapper)
    LinearLayout ratingWrapper;
    @InjectView(R.id.track_rating_bar)
    RatingBar trackRating;
    @InjectView(R.id.loveWrapper)
    LinearLayout loveWrapper;
    @InjectView(R.id.lfmLove)
    ImageButton lfmLoveButton;

	// Injects
	@Inject protected Bus bus;
	@Inject ActiveFragmentProvider afProvide;

    private ShareActionProvider mShareActionProvider;

	private boolean userChangingVolume;
    private int previousVol;
	private Timer progressUpdateTimer;
	private TimerTask progressUpdateTask;


	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
		afProvide.addActiveFragment(this);
		userChangingVolume = false;
		bus.post(new MessageEvent(UserInputEvent.RequestMainViewUpdate));
		SetTextViewTypeface();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) {
		return inflater.inflate(R.layout.ui_fragment_main, container, false);
	}

	@Override
	public void onStart()
	{
		super.onStart();
		afProvide.addActiveFragment(this);
		RegisterListeners();
		bus.post(new MessageEvent(UserInputEvent.RequestMainViewUpdate));

	}

	@Override
	public void onResume()
	{
		super.onResume();
		afProvide.addActiveFragment(this);
		bus.post(new MessageEvent(UserInputEvent.RequestMainViewUpdate));
		bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.NowPlayingPosition,true)));
	}

	@Override
	public void onPause()
	{
		afProvide.removeActiveFragment(this);
		super.onPause();
	}

	@Override
	public void onStop()
	{
		afProvide.removeActiveFragment(this);
		super.onStop();
	}

	@Override
	public void onDestroy()
	{
		afProvide.removeActiveFragment(this);
		super.onDestroy();
	}

	/**
	 * Sets the typeface of the text views in the main activity to roboto.
	 */
	private void SetTextViewTypeface()
	{		/* Marquee Hack */
		try
		{
			artistLabel.setSelected(true);
			titleLabel.setSelected(true);
			albumLabel.setSelected(true);
			yearLabel.setSelected(true);

			Typeface robotoLight = Typeface.createFromAsset(getActivity().getAssets(), "fonts/roboto_light.ttf");
			Typeface robotoRegular = Typeface.createFromAsset(getActivity().getAssets(), "fonts/roboto_regular.ttf");
			artistLabel.setTypeface(robotoLight);
			titleLabel.setTypeface(robotoLight);
			albumLabel.setTypeface(robotoLight);
			yearLabel.setTypeface(robotoLight);
			trackProgressCurrent.setTypeface(robotoRegular);
			trackDuration.setTypeface(robotoRegular);
		} catch (Exception ignore)
		{

		}
	}

    @Override
    public void onCreateOptionsMenu (Menu menu, MenuInflater inflater)
    {
        inflater.inflate(R.menu.menu, menu);
        MenuItem shareItem = menu.findItem(R.id.main_menu_share);

        mShareActionProvider = (ShareActionProvider) shareItem.getActionProvider();
    }

    private void setShareIntent(Intent shareIntent)
    {
        if (mShareActionProvider != null) mShareActionProvider.setShareIntent(shareIntent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case R.id.main_menu_share:
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Now Playing: " + artistLabel.getText() + " - " + titleLabel.getText());
                setShareIntent(shareIntent);
                return true;
            default:
                return false;
        }
    }


	/**
	 * Registers the listeners for the interface elements used for interaction.
	 */
	private void RegisterListeners()
	{
		try
		{
            ratingWrapper.setVisibility(View.INVISIBLE);
            loveWrapper.setVisibility(View.INVISIBLE);
            trackRating.setOnRatingBarChangeListener(ratingChangeListener);
            lfmLoveButton.setOnClickListener(lfmLoveClicked);

			playPauseButton.setOnClickListener(playButtonListener);
			previousButton.setOnClickListener(previousButtonListener);
			nextButton.setOnClickListener(nextButtonListener);
			volumeSlider.setOnSeekBarChangeListener(volumeChangeListener);
			trackProgressSlider.setOnSeekBarChangeListener(durationSeekBarChangeListener);
			stopButton.setOnClickListener(stopButtonListener);
			stopButton.setEnabled(false);
			muteButton.setOnClickListener(muteButtonListener);
			scrobbleButton.setOnClickListener(scrobbleButtonListener);
			shuffleButton.setOnClickListener(shuffleButtonListener);
			repeatButton.setOnClickListener(repeatButtonListener);
			connectivityIndicator.setOnClickListener(connectivityIndicatorListener);
			connectivityIndicator.setOnLongClickListener(connectivityIndicatorLongClickListener);
            albumCover.setOnClickListener(coverOnClick);
		} catch (Exception ignore)
		{

		}

	}

    public void updateRating(float rating){
        if(trackRating!=null){
            trackRating.setRating(rating);
        }
    }

    private RatingBar.OnRatingBarChangeListener ratingChangeListener = new RatingBar.OnRatingBarChangeListener(){
        @Override
        public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
            bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.NowPlayingRating, v)));
        }
    };

	/**
	 * Given a boolean state this function updates the Scrobbler button with the proper state.
	 * Also it updates the internal MainActivityState object.
	 *
	 * @param state If true it means that the scrobbler is active, false is used for inactive.
	 */
	public void updateScrobblerButtonState(boolean state)
	{
		if(scrobbleButton==null) return;
		if (state)
		{
			scrobbleButton.setImageResource(R.drawable.ic_media_scrobble_red);
		} else
		{
			scrobbleButton.setImageResource(R.drawable.ic_media_scrobble_off);
		}
	}

	public void resetAlbumCover()
	{
		if(albumCover==null) return;
		albumCover.setImageResource(R.drawable.ic_image_no_cover);
	}

	public void updateAlbumCover(Bitmap cover)
	{
		if(albumCover==null) return;
		albumCover.setImageBitmap(cover);
	}

	/**
	 * Given a boolean state value this function updates the shuffle button with the proper state.
	 * Also it updates the internal MainActivityState object.
	 *
	 * @param state True is used to represent active shuffle, false is used for inactive.
	 */
	public void updateShuffleButtonState(boolean state)
	{
		if(shuffleButton==null) return;
		if (state)
		{
			shuffleButton.setImageResource(R.drawable.ic_media_shuffle);
		} else
		{
			shuffleButton.setImageResource(R.drawable.ic_media_shuffle_off);
		}
	}

	public void updateRepeatButtonState(boolean state)
	{
		if(repeatButton==null) return;
		if (state)
		{
			repeatButton.setImageResource(R.drawable.ic_media_repeat);
		} else
		{
			repeatButton.setImageResource(R.drawable.ic_media_repeat_off);
		}
	}

	public void updateMuteButtonState(boolean state)
	{
		if(muteButton==null) return;
		if (state)
		{
			muteButton.setImageResource(R.drawable.ic_media_mute_active);
		} else
		{
			muteButton.setImageResource(R.drawable.ic_media_mute_full);
		}
	}

	public void updateVolumeData(int volume)
	{
		if(volumeSlider==null) return;
		if (!userChangingVolume)
			volumeSlider.setProgress(volume);
	}

	public void updatePlayState(PlayState playState)
	{
		if(playPauseButton==null||stopButton==null) return;
		switch (playState)
		{
			case Playing:
				playPauseButton.setImageResource(R.drawable.ic_media_pause);
				playPauseButton.setTag("Playing");
				stopButton.setImageResource(R.drawable.ic_media_stop);
				stopButton.setEnabled(true);				/* Start the animation if the track is playing*/
                bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.NowPlayingPosition, true)));
				trackProgressAnimation();
				break;
			case Paused:
				playPauseButton.setImageResource(R.drawable.ic_media_play);
				playPauseButton.setTag("Paused");
				stopButton.setEnabled(true);
                /* Stop the animation if the track is paused*/
				stopTrackProgressAnimation();
				break;
			case Stopped:
                /* Stop the animation if the track is paused*/
				stopTrackProgressAnimation();
				activateStoppedState();
			case Undefined:
				playPauseButton.setImageResource(R.drawable.ic_media_play);
				stopButton.setImageResource(R.drawable.ic_media_stop_disabled);
				stopButton.setEnabled(false);
				break;
		}
	}

	public void updateArtistText(String artist)
	{
		if(artistLabel==null) return;
		artistLabel.setText(artist);
	}

	public void updateTitleText(String title)
	{
		if(titleLabel==null) return;
		titleLabel.setText(title);
	}

	public void updateAlbumText(String album)
	{
		if(albumLabel==null) return;
		albumLabel.setText(album);
	}

	public void updateYearText(String year)
	{
		if(yearLabel==null) return;
		yearLabel.setText(year);
	}

	public void updateConnectivityStatus(ConnectionStatus status)
	{
		if(connectivityIndicator==null) return;
		switch (status)
		{
			case CONNECTION_OFF:
				connectivityIndicator.setImageResource(R.drawable.ic_connectivy_off);
				stopTrackProgressAnimation();
				activateStoppedState();
				break;
			case CONNECTION_ON:
				connectivityIndicator.setImageResource(R.drawable.ic_connectivity_connected);
				break;
			case CONNECTION_ACTIVE:
				connectivityIndicator.setImageResource(R.drawable.ic_connectivity_active);
				//temp
				bus.post(new MessageEvent(UserInputEvent.RequestNowPlayingList));
				break;
		}
	}

	private void activateStoppedState()
	{
		if(trackProgressCurrent==null || trackProgressSlider==null || stopButton==null) return;
		trackProgressSlider.setProgress(0);
		trackProgressCurrent.setText("00:00");
		stopButton.setEnabled(false);
	}

	/**
	 * Responsible for updating the displays and seekbar responsible for the display of the track duration and the
	 * current progress of playback
	 *
	 * @param current Integer represents the current playback position in milliseconds
	 * @param total   Integer represents the total track duration in milliseconds
	 */
	public void updateDurationDisplay(int current, int total)
	{
		if(trackProgressCurrent==null || trackProgressSlider==null || trackDuration==null) return;
		if (total == 0)
		{
			bus.post(new MessageEvent(UserInputEvent.RequestPosition));
			return;
		}
		int currentSeconds = current / 1000;
		int totalSeconds = total / 1000;

		int currentMinutes = currentSeconds / 60;
		int totalMinutes = totalSeconds / 60;

		currentSeconds %= 60;
		totalSeconds %= 60;

		trackDuration.setText(String.format("%02d:%02d", totalMinutes, totalSeconds));
		trackProgressCurrent.setText(String.format("%02d:%02d", currentMinutes, currentSeconds));

		trackProgressSlider.setMax(total);
		trackProgressSlider.setProgress(current);

		trackProgressAnimation();
	}

	/**
	 * Starts the progress animation when called. If It was previously running then it restarts it.
	 */
	private void trackProgressAnimation()
	{
		if(!isVisible()) return;
        /* If the scheduled tasks is not null then cancel it and clear it along with the timer to create them anew */
		final int timerPeriod = 100;
		stopTrackProgressAnimation();
		if (!stopButton.isEnabled() || playPauseButton.getTag() == "Paused") return;
		progressUpdateTimer = new Timer(true);
		progressUpdateTask = new TimerTask()
		{
			@Override
			public void run()
			{
				int currentProgress = trackProgressSlider.getProgress() / 1000;
				final int currentMinutes = currentProgress / 60;
				final int currentSeconds = currentProgress % 60;
                //TODO: check if activity is null
				getActivity().runOnUiThread(new Runnable()
				{
					public void run()
					{
						trackProgressSlider.setProgress(trackProgressSlider.getProgress() + timerPeriod);
						trackProgressCurrent.setText(String.format("%02d:%02d", currentMinutes, currentSeconds));
					}
				});

			}
		};
		progressUpdateTimer.schedule(progressUpdateTask, 0, timerPeriod);
	}

	/**
	 * If the track progress animation is running the the function stops it.
	 */
	private void stopTrackProgressAnimation()
	{
		if (progressUpdateTask != null)
		{
			progressUpdateTask.cancel();
			progressUpdateTask = null;
			progressUpdateTimer.cancel();
			progressUpdateTimer.purge();
			progressUpdateTimer = null;
		}

	}

	private View.OnClickListener playButtonListener = new View.OnClickListener()
	{

		public void onClick(View v)
		{
			bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.PlayerPlayPause, true)));
		}
	};

	private View.OnClickListener previousButtonListener = new View.OnClickListener()
	{

		public void onClick(View v)
		{
			bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.PlayerPrevious, true)));
		}
	};

	private View.OnClickListener nextButtonListener = new View.OnClickListener()
	{

		public void onClick(View v)
		{
			bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.PlayerNext, true)));
		}
	};

	private View.OnClickListener stopButtonListener = new View.OnClickListener()
	{

		public void onClick(View v)
		{
			bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.PlayerStop, true)));
		}
	};

	private View.OnClickListener muteButtonListener = new View.OnClickListener()
	{

		public void onClick(View v)
		{
			bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.PlayerMute, true)));
		}
	};

	private View.OnClickListener scrobbleButtonListener = new View.OnClickListener()
	{

		public void onClick(View v)
		{
			bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.PlayerScrobble, true)));
		}
	};

	private View.OnClickListener shuffleButtonListener = new View.OnClickListener()
	{

		public void onClick(View v)
		{
			bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.PlayerShuffle, Const.TOGGLE)));
		}
	};

	private View.OnClickListener repeatButtonListener = new View.OnClickListener()
	{

		public void onClick(View v)
		{
			bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.PlayerRepeat, true)));
		}
	};
	private View.OnClickListener connectivityIndicatorListener = new View.OnClickListener()
	{

		public void onClick(View v)
		{
			bus.post(new MessageEvent(UserInputEvent.StartConnection));
		}
	};

	private View.OnLongClickListener connectivityIndicatorLongClickListener = new View.OnLongClickListener()
	{
		@Override
		public boolean onLongClick(View view)
		{
			bus.post(new MessageEvent(UserInputEvent.ResetConnection));
			return false;
		}
	};

	private SeekBar.OnSeekBarChangeListener volumeChangeListener = new SeekBar.OnSeekBarChangeListener()
	{

		public void onStopTrackingTouch(SeekBar seekBar)
		{
			userChangingVolume = false;

		}

		public void onStartTrackingTouch(SeekBar seekBar)
		{
			userChangingVolume = true;

		}

		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
		{
			if (fromUser)
			{
				bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.PlayerVolume, String.valueOf(seekBar.getProgress()))));
			}
		}
	};

	private SeekBar.OnSeekBarChangeListener durationSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener()
	{
		public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
		{
			if (fromUser)
			{
                if (progress != previousVol) {
                    bus.post(new MessageEvent(ProtocolEvent.UserAction,new UserAction(Protocol.NowPlayingPosition,String.valueOf(progress))));
                    previousVol = progress;
                }

			}
		}

		public void onStartTrackingTouch(SeekBar seekBar)
		{
		}

		public void onStopTrackingTouch(SeekBar seekBar)
		{
		}
	};

    private ImageView.OnClickListener coverOnClick = new ImageView.OnClickListener() {

        boolean isActive = false;
        @Override
        public void onClick(View view) {

            if (!isActive) {
                int fadeInDuration = 600;
                int timeBetween = 3000;
                int fadeOutDuration = 1000;

                Animation fadeIn = new AlphaAnimation(0, 1);
                fadeIn.setInterpolator(new DecelerateInterpolator());
                fadeIn.setDuration(fadeInDuration);

                Animation fadeOut = new AlphaAnimation(1, 0);
                fadeOut.setInterpolator(new AccelerateInterpolator());
                fadeOut.setStartOffset(fadeInDuration + timeBetween);
                fadeOut.setDuration(fadeOutDuration);

                AnimationSet animation = new AnimationSet(false);
                animation.addAnimation(fadeIn);
                animation.addAnimation(fadeOut);
                animation.setRepeatCount(1);

                ratingWrapper.startAnimation(animation);
                loveWrapper.startAnimation(animation);
                animation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                        isActive = true;
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        isActive = false;
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                        ||
                    }
                });
            }
        }
    };

    private ImageButton.OnClickListener lfmLoveClicked = new ImageButton.OnClickListener(){

        @Override
        public void onClick(View view) {
            bus.post(new MessageEvent(ProtocolEvent.UserAction, new UserAction(Protocol.NowPlayingLfmRating, Const.TOGGLE)));
        }
    };
}