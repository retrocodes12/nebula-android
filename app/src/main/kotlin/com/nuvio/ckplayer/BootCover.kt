package com.nuvio.ckplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The boot screen's place over Home: up while [booting], never while the who's-watching picker is [asking].
 *
 * It has to be up on the very frame the picker leaves. An AnimatedVisibility that goes from hidden to visible draws its
 * first frame from the enter's start state, even at 0 ms, and for that frame Home showed through (his 11-10 "after i
 * select the profile the home screen flashes for like a millisec before the loading screen appears"; 1.109.0 put the
 * picker first with `visible = booting && !asking, enter = fadeIn(tween(0))`). So the cover is composed only once nobody
 * is being asked, and it starts out already visible: nothing to enter, only the fade out once Home is ready.
 * BootCoverDeviceTest holds this and the old way against a red page, frame by frame.
 */
@Composable
internal fun BootCover(booting: Boolean, asking: Boolean, content: @Composable () -> Unit) {
    if (!asking) {
        val shown = remember { MutableTransitionState(booting) }
        shown.targetState = booting
        AnimatedVisibility(visibleState = shown, enter = EnterTransition.None, exit = fadeOut(tween(320))) { content() }
    }
}
