package com.example.ui.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.SkeletonPreviewCard

@Composable
fun HomeSkeletonContent(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxWidth().testTag("home_skeleton"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SkeletonPreviewCard()
    SkeletonPreviewCard()
    SkeletonPreviewCard()
  }
}

@Composable
fun InsightsSkeletonContent(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxWidth().testTag("insights_skeleton"),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    SkeletonPreviewCard()
    SkeletonPreviewCard()
    SkeletonPreviewCard()
  }
}
