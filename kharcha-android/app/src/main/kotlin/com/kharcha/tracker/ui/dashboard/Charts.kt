package com.kharcha.tracker.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.kharcha.tracker.ui.dashboard.ChartColors
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharcha.tracker.core.util.Format
import com.kharcha.tracker.ui.dashboard.StackedBucket
import com.kharcha.tracker.ui.dashboard.CategorySlice

@Composable
fun StackedBarChart(
    buckets: List<StackedBucket>,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 120.dp,
    barWidth: Dp = 24.dp,
    barSpacing: Dp = 8.dp
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .height(itemHeight)
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        items(buckets) { bucket ->
            Column(
                modifier = Modifier.width(barWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                val maxTotal = buckets.maxOfOrNull { it.total } ?: 0.0
                val parts = bucket.parts
                val partsTotal = parts.sumOf { it.value }.coerceAtLeast(1.0)
                for (part in parts.reversed()) {
                    val ratio = if (partsTotal > 0) part.value / partsTotal else 1.0 / parts.size.coerceAtLeast(1)
                    val partHeight = (ratio * 80.0).toFloat().coerceIn(16f, 80f).toDouble().dp
                    Box(
                        modifier = Modifier
                            .width(barWidth - 4.dp)
                            .height(partHeight)
                            .background(Color(ChartColors.toColor(part.color)))
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bucket.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF64748B),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PieChart(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp
) {
    Row(
        modifier = modifier
            .width(size)
            .height(size)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val grand = slices.sumOf { it.value }.coerceAtLeast(1.0)
        for (slice in slices) {
            val ratio = slice.value / grand
            val sliceSize = (ratio * 80.0).toFloat().coerceIn(16f, 80f).toDouble().dp
            Box(
                modifier = Modifier
                    .width(sliceSize)
                    .height(sliceSize)
                    .background(Color(ChartColors.toColor(slice.color)))
            )
        }
    }
}

@Composable
fun ChartLegend(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        for (slice in slices) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(ChartColors.toColor(slice.color)))
                )
                Text(
                    text = "${slice.label}: ${Format.amount(slice.value, "INR")}",
                    modifier = Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}