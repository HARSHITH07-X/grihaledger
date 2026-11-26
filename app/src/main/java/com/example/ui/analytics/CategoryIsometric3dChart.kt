package com.example.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.analytics.CategoryBreakdownItem
import com.example.domain.analytics.LedgerAnalytics
import com.example.domain.model.Money
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalizedLabels
import com.example.ui.theme.GrihaTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private data class Vec3(val x: Float, val y: Float, val z: Float)

private data class ProjectedPillarHitbox(
  val item: CategoryBreakdownItem,
  val centerX: Float,
  val minX: Float,
  val maxX: Float,
  val minY: Float,
  val maxY: Float,
  val depth: Float,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryIsometric3dChart(
  items: List<CategoryBreakdownItem>,
  totalPaise: Long,
  onSliceTap: (CategoryBreakdownItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val language = LocalAppLanguage.current
  val topItems = remember(items) { items.take(6) }
  val maxAmount = remember(topItems) { topItems.maxOfOrNull { it.amountPaise }?.coerceAtLeast(1L) ?: 1L }

  var yawDeg by remember { mutableFloatStateOf(32f) }
  var pitchDeg by remember { mutableFloatStateOf(28f) }
  var autoOrbit by remember { mutableStateOf(false) }
  var focusedCategoryId by remember(topItems) {
    mutableStateOf(topItems.firstOrNull()?.categoryId)
  }

  val hitboxes = remember { mutableListOf<ProjectedPillarHitbox>() }

  LaunchedEffect(autoOrbit) {
    if (!autoOrbit) return@LaunchedEffect
    var lastNanos = 0L
    while (true) {
      withFrameNanos { frameNanos ->
        if (lastNanos != 0L) {
          val dtSec = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
          yawDeg = (yawDeg + dtSec * 24f) % 360f
        }
        lastNanos = frameNanos
      }
    }
  }

  val stageTopColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val stageBottomColor = MaterialTheme.colorScheme.surfaceContainerLow
  val gridRingColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
  val totalFormatted = Money(totalPaise).formatEnIn(includePaise = totalPaise % 100L != 0L)

  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column {
        Text(
          text = totalFormatted,
          style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(R.string.insights_3d_hint),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      FilterChip(
        selected = autoOrbit,
        onClick = { autoOrbit = !autoOrbit },
        label = { Text(stringResource(R.string.insights_3d_spin)) },
        leadingIcon = {
          Icon(
            imageVector = Icons.Outlined.Autorenew,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        colors =
          FilterChipDefaults.filterChipColors(
            selectedContainerColor = GrihaTheme.colors.goodContainer,
            selectedLabelColor = GrihaTheme.colors.onGoodContainer,
          ),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("categories_3d_spin_toggle"),
      )
    }

    Canvas(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(248.dp)
          .clip(MaterialTheme.shapes.medium)
          .background(
            Brush.verticalGradient(
              colors = listOf(stageTopColor.copy(alpha = 0.45f), stageBottomColor.copy(alpha = 0.7f))
            )
          )
          .pointerInput(topItems) {
            detectDragGestures { change, dragAmount ->
              change.consume()
              autoOrbit = false
              yawDeg = (yawDeg + dragAmount.x * 0.42f) % 360f
              pitchDeg = (pitchDeg - dragAmount.y * 0.28f).coerceIn(14f, 54f)
            }
          }
          .pointerInput(topItems) {
            detectTapGestures { tapOffset ->
              // Match front-most pillar hitbox first (highest depth closer to viewer)
              val hit =
                hitboxes
                  .sortedByDescending { it.depth }
                  .firstOrNull { box ->
                    tapOffset.x in (box.minX - 8f)..(box.maxX + 8f) &&
                      tapOffset.y in (box.minY - 8f)..(box.maxY + 8f)
                  }
              if (hit != null) {
                focusedCategoryId = hit.item.categoryId
                onSliceTap(hit.item)
              }
            }
          }
          .testTag("categories_3d_canvas"),
    ) {
      hitboxes.clear()
      val yawRad = Math.toRadians(yawDeg.toDouble()).toFloat()
      val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
      val cosY = cos(yawRad)
      val sinY = sin(yawRad)
      val cosP = cos(pitchRad)
      val sinP = sin(pitchRad)

      val cx = size.width / 2f
      val cy = size.height * 0.68f
      val worldScale = min(size.width, size.height) * 0.62f
      val cameraDist = 4.6f

      fun rotateAndProject(p: Vec3): Pair<Offset, Float> {
        // Rotate around Y axis (yaw)
        val x1 = p.x * cosY - p.z * sinY
        val z1 = p.x * sinY + p.z * cosY
        // Rotate around X axis (pitch)
        val y2 = p.y * cosP - z1 * sinP
        val z2 = p.y * sinP + z1 * cosP
        val perspective = cameraDist / (cameraDist - z2).coerceAtLeast(1.2f)
        val sx = cx + x1 * worldScale * perspective
        val sy = cy - y2 * worldScale * perspective
        return Offset(sx, sy) to z2
      }

      // Draw circular stage floor rings at y = 0
      val ringSegments = 36
      for (ringScale in listOf(0.84f, 0.56f)) {
        val ringPath = Path()
        for (s in 0 until ringSegments) {
          val theta = (s.toFloat() / ringSegments) * (2f * PI.toFloat())
          val (pt, _) = rotateAndProject(Vec3(cos(theta) * ringScale, 0f, sin(theta) * ringScale))
          if (s == 0) ringPath.moveTo(pt.x, pt.y) else ringPath.lineTo(pt.x, pt.y)
        }
        ringPath.close()
        drawPath(
          path = ringPath,
          color = gridRingColor,
          style = Stroke(width = if (ringScale > 0.7f) 1.5.dp.toPx() else 1.dp.toPx()),
        )
      }

      if (topItems.isEmpty()) return@Canvas

      data class PillarSpec(
        val item: CategoryBreakdownItem,
        val worldCenter: Vec3,
        val height: Float,
        val radius: Float,
        val sortDepth: Float,
      )

      val count = topItems.size
      val orbitRadius = if (count == 1) 0f else 0.48f
      val halfSize = if (count <= 3) 0.145f else 0.115f

      val specs =
        topItems.mapIndexed { idx, item ->
          val angle = (idx.toFloat() / count.toFloat()) * (2f * PI.toFloat()) - (PI.toFloat() / 6f)
          val px = cos(angle) * orbitRadius
          val pz = sin(angle) * orbitRadius
          val ratio = (item.amountPaise.toFloat() / maxAmount.toFloat()).coerceIn(0.08f, 1f)
          val height = 0.14f + ratio * 0.68f
          val (_, depth) = rotateAndProject(Vec3(px, height * 0.5f, pz))
          PillarSpec(item, Vec3(px, 0f, pz), height, halfSize, depth)
        }.sortedBy { it.sortDepth } // Back-to-front painter's sort

      data class FaceIndices(val a: Int, val b: Int, val c: Int, val d: Int, val shade: Float)
      val sideFaces =
        listOf(
          FaceIndices(0, 1, 5, 4, 0.88f), // Front-ish
          FaceIndices(1, 2, 6, 5, 0.68f), // Right
          FaceIndices(2, 3, 7, 6, 0.58f), // Back
          FaceIndices(3, 0, 4, 7, 0.78f), // Left
        )

      specs.forEach { spec ->
        val cxW = spec.worldCenter.x
        val czW = spec.worldCenter.z
        val r = spec.radius
        val h = spec.height
        val baseColor = Color(spec.item.colorHex)
        val isFocused = spec.item.categoryId == focusedCategoryId

        // Subtle shadow quad on y = 0 offset toward back-right
        val shadowOffset = 0.045f
        val shadowCorners =
          listOf(
            Vec3(cxW - r + shadowOffset, 0f, czW - r + shadowOffset),
            Vec3(cxW + r + shadowOffset, 0f, czW - r + shadowOffset),
            Vec3(cxW + r + shadowOffset, 0f, czW + r + shadowOffset),
            Vec3(cxW - r + shadowOffset, 0f, czW + r + shadowOffset),
          ).map { rotateAndProject(it).first }
        val shadowPath =
          Path().apply {
            moveTo(shadowCorners[0].x, shadowCorners[0].y)
            lineTo(shadowCorners[1].x, shadowCorners[1].y)
            lineTo(shadowCorners[2].x, shadowCorners[2].y)
            lineTo(shadowCorners[3].x, shadowCorners[3].y)
            close()
          }
        drawPath(shadowPath, color = Color.Black.copy(alpha = 0.14f))

        // 8 vertices of the 3D pillar box
        val vertices =
          listOf(
            Vec3(cxW - r, 0f, czW + r), // 0: bottom-front-left
            Vec3(cxW + r, 0f, czW + r), // 1: bottom-front-right
            Vec3(cxW + r, 0f, czW - r), // 2: bottom-back-right
            Vec3(cxW - r, 0f, czW - r), // 3: bottom-back-left
            Vec3(cxW - r, h, czW + r),  // 4: top-front-left
            Vec3(cxW + r, h, czW + r),  // 5: top-front-right
            Vec3(cxW + r, h, czW - r),  // 6: top-back-right
            Vec3(cxW - r, h, czW - r),  // 7: top-back-left
          )
        val projected = vertices.map { rotateAndProject(it) }

        // Sort side faces back-to-front by average projected Z
        val orderedSides =
          sideFaces
            .map { f ->
              val avgZ =
                (projected[f.a].second + projected[f.b].second + projected[f.c].second + projected[f.d].second) /
                  4f
              f to avgZ
            }
            .sortedBy { it.second }

        // Draw back faces slightly muted and front faces brightly with directional shade
        orderedSides.forEach { (f, _) ->
          val pa = projected[f.a].first
          val pb = projected[f.b].first
          val pc = projected[f.c].first
          val pd = projected[f.d].first
          // Backface culling via 2D cross product (clockwise/counter-clockwise check)
          val cross = (pb.x - pa.x) * (pc.y - pa.y) - (pb.y - pa.y) * (pc.x - pa.x)
          if (cross < 0f) {
            val sideColor = lerp(Color.Black, baseColor, f.shade)
            val facePath =
              Path().apply {
                moveTo(pa.x, pa.y)
                lineTo(pb.x, pb.y)
                lineTo(pc.x, pc.y)
                lineTo(pd.x, pd.y)
                close()
              }
            drawPath(facePath, color = sideColor)
            drawPath(
              facePath,
              color = Color.White.copy(alpha = if (isFocused) 0.25f else 0.08f),
              style = Stroke(width = 1.dp.toPx()),
            )
          }
        }

        // Top lit cap
        val t0 = projected[4].first
        val t1 = projected[5].first
        val t2 = projected[6].first
        val t3 = projected[7].first
        val topPath =
          Path().apply {
            moveTo(t0.x, t0.y)
            lineTo(t1.x, t1.y)
            lineTo(t2.x, t2.y)
            lineTo(t3.x, t3.y)
            close()
          }
        val topColor = lerp(baseColor, Color.White, if (isFocused) 0.34f else 0.24f)
        drawPath(topPath, color = topColor)
        drawPath(
          topPath,
          color = Color.White.copy(alpha = if (isFocused) 0.55f else 0.22f),
          style = Stroke(width = if (isFocused) 1.5.dp.toPx() else 1.dp.toPx()),
        )

        // Record screen-space hitbox for tap-to-filter
        val xs = projected.map { it.first.x }
        val ys = projected.map { it.first.y }
        hitboxes.add(
          ProjectedPillarHitbox(
            item = spec.item,
            centerX = (xs.min() + xs.max()) / 2f,
            minX = xs.min(),
            maxX = xs.max(),
            minY = ys.min(),
            maxY = ys.max(),
            depth = spec.sortDepth,
          )
        )
      }
    }

    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      topItems.forEach { item ->
        val catName = LocalizedLabels.categoryNameById(item.categoryId, item.categoryName, language)
        val pct = LedgerAnalytics.formatBasisPointsPercent(item.shareBps)
        val isFocused = item.categoryId == focusedCategoryId
        Surface(
          shape = MaterialTheme.shapes.small,
          color =
            if (isFocused) GrihaTheme.colors.goodContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
          modifier =
            Modifier
              .clip(MaterialTheme.shapes.small)
              .clickable {
                focusedCategoryId = item.categoryId
                onSliceTap(item)
              }
              .minimumInteractiveComponentSize()
              .testTag("categories_3d_pill_${item.categoryId}"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box(
              modifier =
                Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(Color(item.colorHex)),
            )
            Text(
              text = "$catName · $pct",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Medium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}
