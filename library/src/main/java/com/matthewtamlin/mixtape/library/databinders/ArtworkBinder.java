/*
 * Copyright 2017 Matthew Tamlin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.matthewtamlin.mixtape.library.databinders;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.widget.ImageView;

import com.matthewtamlin.java_utilities.testing.Tested;
import com.matthewtamlin.mixtape.library.data.DisplayableDefaults;
import com.matthewtamlin.mixtape.library.data.LibraryItem;
import com.matthewtamlin.mixtape.library.data.LibraryReadException;

import java.util.HashMap;
import java.util.Iterator;

import static com.matthewtamlin.java_utilities.checkers.NullChecker.checkNotNull;

/**
 * Binds artwork data from LibraryItems to ImageViews. Data is cached as it is loaded to improve
 * future performance, and asynchronous processing is only used if data is not already cached. By
 * default a fade-in effect is used when artwork is bound, but this can be disabled if desired.
 */
@Tested(testMethod = "automated")
public class ArtworkBinder implements DataBinder<LibraryItem, ImageView> {
	/**
	 * A record of all bind tasks currently in progress. Each task is mapped to the target
	 * ImageView.
	 */
	private final HashMap<ImageView, BinderTask> tasks = new HashMap<>();

	/**
	 * Stores artwork to increase performance and efficiency.
	 */
	private final LruCache<LibraryItem, Drawable> cache;

	/**
	 * Supplies the default artwork.
	 */
	private final DisplayableDefaults defaults;

	/**
	 * The duration to use when transitioning artwork, measured in milliseconds.
	 */
	private int fadeInDurationMs = 300;

	/**
	 * The width to use when decoding artwork if the optimal dimension cannot be inferred from the
	 * target ImageView.
	 */
	private int fallbackDecodingWidth = 300;

	/**
	 * The height to use when decoding artwork if the optimal dimension cannot be inferred from the
	 * target ImageView.
	 */
	private int fallbackDecodingHeight = 300;

	/**
	 * Constructs a new ArtworkBinder.
	 *
	 * @param cache
	 * 		stores subtitles to increase performance and efficiency, not null
	 * @param defaults
	 * 		supplies the default subtitle, not null
	 * @throws IllegalArgumentException
	 * 		if {@code cache} is null
	 * @throws IllegalArgumentException
	 * 		if {@code defaults} is null
	 */
	public ArtworkBinder(final LruCache<LibraryItem, Drawable> cache,
			final DisplayableDefaults defaults) {
		this.cache = checkNotNull(cache, "cache cannot be null.");
		this.defaults = checkNotNull(defaults, "defaults cannot be null.");
	}

	@Override
	public void bind(final ImageView imageView, final LibraryItem data) {
		checkNotNull(imageView, "imageView cannot be null");

		// There should never be more than one task operating on the same ImageView concurrently
		cancel(imageView);

		// Create, register and start task
		final BinderTask task = new BinderTask(imageView, data);
		tasks.put(imageView, task);
		task.execute();
	}

	@Override
	public void cancel(final ImageView imageView) {
		final AsyncTask existingTask = tasks.get(imageView);

		if (existingTask != null) {
			existingTask.cancel(false);
			tasks.remove(imageView);
		}
	}

	@Override
	public void cancelAll() {
		final Iterator<ImageView> imageViewIterator = tasks.keySet().iterator();

		while (imageViewIterator.hasNext()) {
			final AsyncTask existingTask = tasks.get(imageViewIterator.next());

			if (existingTask != null) {
				existingTask.cancel(false);
				imageViewIterator.remove();
			}
		}
	}

	/**
	 * @return the cache used to store artwork, not null
	 */
	public LruCache<LibraryItem, Drawable> getCache() {
		return cache;
	}

	/**
	 * @return the default artwork supplier, not null
	 */
	public DisplayableDefaults getDefaults() {
		return defaults;
	}

	/**
	 * @return the duration used when fading in artwork
	 */
	public int getFadeInDurationMs() {
		return fadeInDurationMs;
	}

	/**
	 * Sets the duration to use when fading in artwork.
	 *
	 * @param durationMs
	 * 		the duration to use, measured in milliseconds, not less than zero
	 */
	public void setFadeInDurationMs(final int durationMs) {
		fadeInDurationMs = durationMs;
	}

	/**
	 * @return the width dimension to use when decoding artwork if the optimal dimension cannot be
	 * inferred from the target ImageView
	 */
	public int getFallbackDecodingWidth() {
		return fallbackDecodingWidth;
	}

	/**
	 * Sets the width to use when decoding artwork if the target ImageView cannot return its
	 * dimensions.
	 */
	public void setFallbackDecodingWidth(final int width) {
		this.fallbackDecodingWidth = width;
	}

	/**
	 * @return the height dimension to use when decoding artwork if the optimal dimension cannot be
	 * inferred from the target ImageView
	 */
	public int getFallbackDecodingHeight() {
		return fallbackDecodingHeight;
	}

	/**
	 * Sets the height to use when decoding artwork if the target ImageView cannot return its
	 * dimensions.
	 */
	public void setFallbackDecodingHeight(final int height) {
		this.fallbackDecodingHeight = height;
	}

	/**
	 * Task for asynchronously loading data and binding it to the UI when available.
	 */
	private class BinderTask extends AsyncTask<Void, Void, Drawable> {
		/**
		 * The ImageView to bind data to.
		 */
		private final ImageView imageView;

		/**
		 * The LibraryItem to source the artwork from.
		 */
		private final LibraryItem data;

		/**
		 * The width to use when decoding the artwork, measured in pixels.
		 */
		private int imageWidth;

		/**
		 * The height to use when decoding the artwork, measured in pixels.
		 */
		private int imageHeight;

		/**
		 * Constructs a new BinderTask.
		 *
		 * @param imageView
		 * 		the ImageView to bind data to, not null
		 * @param data
		 * 		the LibraryItem to source the artwork from
		 * @throws IllegalArgumentException
		 * 		if {@code imageView} is null
		 */
		public BinderTask(final ImageView imageView, final LibraryItem data) {
			this.imageView = checkNotNull(imageView, "imageView cannot be null");
			this.data = data;
		}

		@Override
		public void onPreExecute() {
			if (!isCancelled()) {
				imageView.setImageDrawable(null);

				// Read the dimensions from the image view and select decoding values
				final int viewWidth = imageView.getWidth();
				final int viewHeight = imageView.getHeight();
				imageWidth = viewWidth == 0 ? fallbackDecodingWidth : viewWidth;
				imageHeight = viewHeight == 0 ? fallbackDecodingHeight : viewHeight;
			}
		}

		@Override
		public Drawable doInBackground(final Void... params) {
			if (isCancelled() || data == null) {
				return null;
			}

			final Drawable cachedArtwork = cache.get(data);

			if (cachedArtwork == null) {
				try {
					final Drawable loadedArtwork = data.getArtwork(imageWidth, imageHeight);

					if (loadedArtwork != null) {
						cache.put(data, loadedArtwork);
					}

					return loadedArtwork;
				} catch (final LibraryReadException e) {
					return defaults.getArtwork();
				}
			} else {
				return cachedArtwork;
			}
		}

		@Override
		public void onPostExecute(final Drawable artwork) {
			// Skip the animation if it isn't necessary
			if (fadeInDurationMs <= 0 || artwork == null) {
				if (!isCancelled()) {
					imageView.setImageDrawable(null); // Resets view
					imageView.setImageDrawable(artwork);
				}
			} else {
				// Animation to fade in from fully invisible to fully visible
				final ValueAnimator fadeInAnimation = ValueAnimator.ofFloat(0, 1);

				// When the animations starts, bind the artwork but make it invisible
				fadeInAnimation.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationStart(final Animator animation) {
						// If the task has been cancelled, it must not modify the UI
						if (!isCancelled()) {
							imageView.setAlpha(0f);
							imageView.setImageDrawable(null); // Resets ensures image changes
							imageView.setImageDrawable(artwork);
						}
					}
				});

				// As the animation progresses, fade-in the artwork by changing the transparency
				fadeInAnimation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
					@Override
					public void onAnimationUpdate(final ValueAnimator animation) {
						// If the task has been cancelled, the animation must also be cancelled
						if (isCancelled()) {
							fadeInAnimation.cancel();
						} else {
							final Float value = (Float) animation.getAnimatedValue();
							imageView.setAlpha(value);
						}
					}
				});

				fadeInAnimation.setDuration(fadeInDurationMs);
				fadeInAnimation.start();
			}
		}
	}
}