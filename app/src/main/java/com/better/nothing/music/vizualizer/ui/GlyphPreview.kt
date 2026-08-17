package com.better.nothing.music.vizualizer.ui

import com.better.nothing.music.vizualizer.model.DeviceProfile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativePaint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.min
import kotlin.math.atan2
import android.graphics.PathMeasure

@Composable
fun GlyphPreview(
    vizStateProvider: () -> FloatArray,
    device: Int,
    modifier: Modifier = Modifier
) {
    var isFullScreen by remember { mutableStateOf(false) }

    val content = @Composable { m: Modifier ->
        GlyphPreviewContent(
            vizStateProvider = vizStateProvider,
            device = device,
            modifier = m.clickable { isFullScreen = !isFullScreen }
        )
    }

    if (isFullScreen) {
        Dialog(
            onDismissRequest = { isFullScreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { isFullScreen = false },
                contentAlignment = Alignment.Center
            ) {
                GlyphPreviewContent(
                    vizStateProvider = vizStateProvider,
                    device = device,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        content(modifier)
    }
}

@Composable
fun GlyphPreviewContent(
    vizStateProvider: () -> FloatArray,
    device: Int,
    modifier: Modifier = Modifier
) {
    val color = Color.White
    val baseOpacity = 0.08f

    val parser = remember { PathParser() }
    val paths = remember {
        mutableMapOf<String, Path>().apply {
            // --- Phone (1) ---
            put("p1_cam", parser.parsePathString("M9.704,68.077L9.704,32.177C9.704,20.184 19.241,10.363 31.233,10.01C43.226,9.657 53.314,18.909 54.021,30.885C54.038,31.221 53.917,31.548 53.693,31.798C53.461,32.039 53.142,32.177 52.806,32.177L49.136,32.177C48.498,32.177 47.964,31.677 47.921,31.04C47.335,22.691 40.383,16.109 31.888,16.109C23.014,16.109 15.82,23.303 15.82,32.177L15.82,68.077C15.82,76.951 23.014,84.145 31.888,84.145C40.762,84.145 47.955,76.951 47.955,68.077L47.955,57.696C47.955,57.024 48.498,56.472 49.179,56.472L52.84,56.472C53.512,56.472 54.064,57.015 54.064,57.696L54.064,68.077C54.064,76.003 49.834,83.318 42.976,87.281C36.118,91.244 27.658,91.244 20.8,87.281C13.934,83.318 9.704,75.994 9.704,68.077Z").toPath())
            put("p1_slash", parser.parsePathString("M120.51,63.373C119.812,64.208 119.605,65.354 119.976,66.379C120.346,67.405 121.242,68.154 122.31,68.344C123.379,68.533 124.481,68.137 125.179,67.301L158.891,27.128C159.976,25.836 159.804,23.914 158.512,22.829C157.22,21.743 155.298,21.916 154.213,23.208L120.51,63.373Z").toPath())
            put("p1_ring_bl", parser.parsePathString("M123.153,287.927C112.9,291.329 102.057,293.12 91,293.12C60.17,293.12 31.01,279.2 11.63,255.23C9.6,252.73 8.5,249.61 8.5,246.4L8.5,231.182L14.61,231.182L14.61,246.4C14.61,248.21 15.23,249.97 16.37,251.39C34.6,273.91 62.02,287.01 91,287.01C101.316,287.01 111.435,285.35 121.011,282.196L123.153,287.927Z").toPath())
            put("p1_ring_br", parser.parsePathString("M121.011,282.196C138.337,276.49 153.889,265.893 165.63,251.39C166.77,249.97 167.39,248.21 167.39,246.4L167.39,194.58C167.39,192.9 168.75,191.53 170.44,191.53C172.12,191.53 173.49,192.89 173.49,194.58L173.5,194.58L173.5,246.4C173.5,249.61 172.4,252.73 170.37,255.23C157.941,270.603 141.489,281.842 123.153,287.927L121.011,282.196Z").toPath())
            put("p1_ring_tr", parser.parsePathString("M27.716,118.88L23.393,114.558C41.74,98.337 65.477,89.123 90.35,88.96C120.93,88.76 150,102.3 169.54,125.83C170.24,126.67 170.43,127.82 170.05,128.84C169.68,129.86 168.78,130.61 167.7,130.79C166.63,130.97 165.54,130.56 164.84,129.73C146.47,107.6 119.14,94.88 90.38,95.06C67.131,95.214 44.927,103.784 27.716,118.88Z").toPath())
            put("p1_ring_tl", parser.parsePathString("M8.5,231.182L8.5,135.68C8.5,132.47 9.6,129.35 11.63,126.85C15.231,122.398 19.169,118.292 23.393,114.558L27.716,118.88C23.636,122.459 19.836,126.404 16.37,130.69C15.23,132.1 14.61,133.86 14.61,135.67L14.61,231.182L8.5,231.182Z").toPath())
            put("p1_dot", parser.parsePathString("M90.991,371C92.68,371 94.041,369.63 94.041,367.95L94.041,366.115C94.041,364.427 92.671,363.065 90.991,363.065C89.311,363.065 87.941,364.435 87.941,366.115L87.941,367.95C87.941,369.63 89.311,371 90.991,371Z").toPath())
            put("p1_battery", parser.parsePathString("M90.991,356.73C92.68,356.73 94.041,355.36 94.041,353.68L94.041,311.801C94.041,310.112 92.671,308.751 90.991,308.751C89.311,308.751 87.941,310.121 87.941,311.801L87.941,353.68C87.941,355.368 89.311,356.73 90.991,356.73Z").toPath())
            put("p1s_ring", parser.parsePathString("m173.49 194.58c0-1.69-1.37-3.05-3.05-3.05-1.69 0-3.05 1.37-3.05 3.05v51.82c0 1.81-0.62 3.57-1.76 4.99-18.23 22.52-45.65 35.62-74.63 35.62s-56.4-13.1-74.63-35.62c-1.14-1.42-1.76-3.18-1.76-4.99v-110.73c-0-1.81 0.62-3.57 1.76-4.98 18.09-22.37 45.25-35.44 74.01-35.63 28.76-0.18 56.09 12.54 74.46 34.67 0.7 0.83 1.79 1.24 2.86 1.06 1.08-0.18 1.98-0.93 2.35-1.95 0.38-1.02 0.19-2.17-0.51-3.01-19.54-23.53-48.61-37.07-79.19-36.87-30.6 0.2-59.48 14.1-78.72 37.89-2.03 2.5-3.13 5.62-3.13 8.83v110.72c-0 3.21 1.1 6.33 3.13 8.83 19.38 23.97 48.54 37.89 79.37 37.89s59.99-13.92 79.37-37.89c2.03-2.5 3.13-5.62 3.13-8.83v-51.82h-0.01z").toPath())

            // --- Phone (2) ---
            put("p2_0", parser.parsePathString("M17.883,51.449l-0,-25.117c-0,-9.107 7.233,-16.58 16.353,-16.892c9.119,-0.311 16.836,6.662 17.46,15.751c0.042,0.64 0.578,1.141 1.219,1.141l3.686,0c0.337,0 0.657,-0.139 0.891,-0.381c0.234,-0.241 0.354,-0.569 0.337,-0.906c-0.71,-12.451 -11.195,-22.077 -23.671,-21.73c-12.477,0.354 -22.41,10.549 -22.41,23.017l0,25.117c0,0.674 0.546,1.226 1.229,1.226l3.677,0c0.675,0 1.229,-0.544 1.229,-1.226Z").toPath())
            put("p2_1", parser.parsePathString("M51.975,48.161c-0,-0.674 0.544,-1.226 1.228,-1.226l3.677,-0c0.675,-0 1.229,0.544 1.229,1.226l0,17.817c0,8.657 -4.863,16.589 -12.589,20.511c-7.726,3.931 -17.01,3.197 -24.018,-1.901c-0.277,-0.198 -0.449,-0.501 -0.493,-0.829c-0.043,-0.329 0.052,-0.674 0.268,-0.933l2.336,-2.851c0.407,-0.502 1.134,-0.597 1.661,-0.225c5.166,3.663 11.94,4.139 17.564,1.235c5.624,-2.903 9.154,-8.692 9.154,-15.016l-0,-17.816l-0.017,0.008Z").toPath())
            put("p2_2", parser.parsePathString("M154.368,14.853c1.09,-1.297 3.02,-1.461 4.318,-0.381c1.297,1.08 1.462,3.015 0.38,4.312l-33.362,39.701c-0.519,0.623 -1.271,1.011 -2.085,1.08c-0.814,0.069 -1.618,-0.181 -2.241,-0.708l-1.41,-1.184c-0.251,-0.207 -0.407,-0.51 -0.433,-0.83c-0.026,-0.319 0.069,-0.647 0.286,-0.889l34.539,-41.11l0.008,0.009Z").toPath())
            put("p2_ring", parser.parsePathString("M74.634,89.533c35.857,-5.279 71.801,8.96 94.341,37.376c1.054,1.322 0.829,3.249 -0.491,4.303c-1.32,1.055 -3.245,0.83 -4.298,-0.492c-21.177,-26.707 -54.964,-40.09 -88.654,-35.13c-1.079,0.155 -2.166,-0.268 -2.84,-1.132c-0.673,-0.864 -0.845,-2.013 -0.448,-3.032c0.406,-1.02 1.321,-1.746 2.398,-1.901l-0.008,0.008Z").toPath())
            put("p2_19", parser.parsePathString("M49.732,97.623c0.995,-0.458 2.163,-0.345 3.054,0.293c0.891,0.631 1.375,1.695 1.272,2.783c-0.104,1.089 -0.78,2.039 -1.783,2.497c-13.756,6.264 -25.835,15.699 -35.231,27.527c-0.683,0.855 -1.764,1.288 -2.855,1.124c-1.081,-0.164 -1.998,-0.89 -2.405,-1.901c-0.407,-1.019 -0.234,-2.169 0.45,-3.024c10.001,-12.589 22.85,-22.62 37.489,-29.29l0.009,-0.009Z").toPath())
            put("p2_20", parser.parsePathString("M14.625,188.142c-0,1.694 -1.376,3.059 -3.063,3.059c-1.687,-0 -3.063,-1.375 -3.063,-3.059l0,-41.542c0,-1.097 0.588,-2.108 1.532,-2.652c0.951,-0.544 2.119,-0.544 3.062,0c0.953,0.544 1.532,1.555 1.532,2.652l-0,41.542Z").toPath())
            put("p2_21", parser.parsePathString("M17.044,250.861c21.232,26.707 55.105,40.09 88.883,35.13c1.081,-0.155 2.172,0.269 2.846,1.132c0.684,0.855 0.848,2.013 0.45,3.033c-0.407,1.019 -1.324,1.745 -2.406,1.901c-35.949,5.278 -71.984,-8.96 -94.583,-37.377c-0.684,-0.856 -0.857,-2.013 -0.45,-3.024c0.407,-1.02 1.315,-1.746 2.405,-1.901c1.082,-0.164 2.172,0.267 2.855,1.123l-0,-0.017Z").toPath())
            put("p2_22", parser.parsePathString("M170.123,251.638c0.407,1.02 0.233,2.169 -0.451,3.025c-10.001,12.588 -22.849,22.619 -37.488,29.289c-0.995,0.459 -2.163,0.346 -3.055,-0.293c-0.891,-0.64 -1.375,-1.694 -1.271,-2.783c0.103,-1.088 0.778,-2.038 1.782,-2.497c13.757,-6.264 25.834,-15.699 35.231,-27.527c0.683,-0.855 1.765,-1.288 2.855,-1.124c1.082,0.165 1.999,0.891 2.405,1.901l-0.008,0.009Z").toPath())
            put("p2_23", parser.parsePathString("M169.303,190.397c-1.695,-0 -3.063,1.373 -3.063,3.058l0,31.545c0,1.694 1.376,3.059 3.063,3.059c1.687,-0 3.063,-1.374 3.063,-3.059l-0,-31.545c-0,-1.693 -1.376,-3.058 -3.063,-3.058Z").toPath())
            put("p2_24", parser.parsePathString("M90.191,364.357c-1.691,-0 -3.055,1.373 -3.055,3.058l-0,3.231c-0,1.694 1.372,3.059 3.055,3.059c1.682,-0 3.055,-1.374 3.055,-3.059l-0,-3.231c-0,-1.693 -1.373,-3.058 -3.055,-3.058Z").toPath())
            put("p2_battery", parser.parsePathString("M87.136,315.644l-0,39.873c-0,1.693 1.372,3.059 3.055,3.059c1.682,-0 3.055,-1.375 3.055,-3.059l-0,-39.873c-0,-1.097 -0.587,-2.108 -1.527,-2.653c-0.95,-0.544 -2.115,-0.544 -3.055,0c-0.949,0.545 -1.528,1.556 -1.528,2.653Z").toPath())

            // --- Phone (2a) ---
            put("p2a_large", parser.parsePathString("M63.057,55.311c0.524,1.363 -0.156,2.894 -1.52,3.419c-4.942,1.901 -12.013,7.268 -18.387,14.372c-6.355,7.083 -11.63,15.468 -13.376,23.102c-0.326,1.424 -1.744,2.315 -3.169,1.989c-1.424,-0.325 -2.314,-1.744 -1.989,-3.169c2.028,-8.869 7.948,-18.045 14.596,-25.455c6.628,-7.39 14.367,-13.448 20.426,-15.778c1.363,-0.525 2.894,0.156 3.419,1.52Z").toPath())
            put("p2a_medium", parser.parsePathString("M159.648,87.219c1.482,-0 2.68,1.198 2.68,2.68l0,47.64c0,1.483 -1.198,2.681 -2.68,2.681c-1.478,-0 -2.676,-1.198 -2.676,-2.681l0,-47.64c0,-1.482 1.198,-2.68 2.676,-2.68Z").toPath())
            put("p2a_small", parser.parsePathString("M30.754,144.063c1.363,-0.573 2.932,0.066 3.506,1.428c2.167,5.144 7.304,12.329 11.354,15.749c1.129,0.953 1.272,2.642 0.318,3.772c-0.954,1.13 -2.643,1.272 -3.773,0.318c-4.752,-4.013 -10.37,-11.912 -12.833,-17.76c-0.574,-1.363 0.065,-2.933 1.428,-3.507Z").toPath())

            // --- Phone (3a) ---
            put("p3a_large", parser.parsePathString("M23.37,58.781C23.34,58.831 23.32,58.882 23.31,58.932C23.3,58.952 23.29,58.973 23.29,58.993L23.29,59C23.28,59.027 23.27,59.055 23.26,59.082C22.71,60.606 21.02,61.416 19.49,60.9C17.93,60.375 17.07,58.671 17.6,57.102L17.6,57.061C17.94,56.041 18.31,55.035 18.69,54.034L18.7,54.035C19.06,53.083 19.44,52.139 19.84,51.203C20.17,50.415 20.52,49.634 20.87,48.859L20.87,48.858C21.31,47.931 21.74,47.015 22.22,46.107C22.77,45.031 23.34,43.967 23.94,42.919L23.94,42.92C24.45,42.033 24.98,41.155 25.52,40.289L25.52,40.29C25.97,39.563 26.45,38.845 26.93,38.135L26.93,38.134C27.49,37.284 28.08,36.446 28.68,35.619L28.68,35.618C29.31,34.755 29.96,33.908 30.61,33.072C31.33,32.17 32.07,31.285 32.82,30.416L32.82,30.418C33.38,29.778 33.94,29.146 34.52,28.521C34.54,28.497 34.57,28.472 34.59,28.448C34.68,28.349 34.77,28.249 34.86,28.149C35.45,27.525 36.06,26.909 36.66,26.303L36.66,26.302C37.38,25.581 38.11,24.874 38.87,24.179L38.87,24.177C39.73,23.375 40.62,22.593 41.52,21.83L41.53,21.831C42.31,21.172 43.1,20.529 43.91,19.901C44.6,19.373 45.27,18.856 45.97,18.349C46.8,17.744 47.65,17.154 48.49,16.581L48.49,16.578C49.42,15.965 50.34,15.368 51.29,14.792C51.83,14.468 52.41,14.328 52.99,14.355C53.95,14.4 54.88,14.908 55.41,15.793C56.28,17.209 55.84,19.056 54.42,19.922C54.36,19.963 54.28,20.007 54.21,20.047L54.21,20.045C53.54,20.456 52.89,20.878 52.23,21.312L52.23,21.314C51.43,21.839 50.65,22.38 49.87,22.933L49.87,22.934C48.98,23.578 48.1,24.242 47.23,24.926L47.23,24.925C46.49,25.514 45.75,26.117 45.03,26.732L45.03,26.73C44.44,27.235 43.87,27.748 43.29,28.271L43.29,28.273C42.59,28.911 41.9,29.562 41.23,30.227C40.55,30.892 39.89,31.568 39.25,32.258C38.5,33.059 37.77,33.878 37.04,34.712L37.04,34.709C36.53,35.307 36.03,35.913 35.55,36.528C34.95,37.266 34.36,38.015 33.8,38.778L33.81,38.78C33.25,39.54 32.7,40.311 32.16,41.093C31.55,42.002 30.95,42.926 30.36,43.862C29.88,44.666 29.39,45.479 28.93,46.302L28.93,46.3C28.53,47 28.15,47.708 27.78,48.423C27.34,49.26 26.93,50.105 26.52,50.959L26.52,50.96C26.11,51.816 25.73,52.681 25.36,53.553C24.92,54.562 24.51,55.582 24.13,56.613C23.87,57.33 23.61,58.053 23.36,58.78L23.37,58.781Z").toPath())
            put("p3a_medium", parser.parsePathString("M162.87,60.41C164.27,60.36 165.56,61.288 165.91,62.693C166.18,63.799 166.42,64.91 166.64,66.024L166.64,66.025C167.04,68.036 167.37,70.056 167.6,72.08L167.6,72.081C167.83,74.113 168.02,76.149 168.08,78.185C168.17,79.963 168.17,81.743 168.13,83.518C168.07,85.554 167.94,87.585 167.72,89.608L167.74,89.609C167.5,91.909 167.15,94.196 166.7,96.466L166.7,96.465C166.31,98.468 165.85,100.458 165.29,102.428L165.29,102.427C164.82,104.134 164.3,105.826 163.71,107.501C163.03,109.425 162.27,111.325 161.45,113.198L161.45,113.202C160.63,115.062 159.73,116.895 158.76,118.697L158.76,118.698C158.21,119.704 157.66,120.702 157.08,121.687C156.22,123.11 154.38,123.574 152.96,122.726C151.54,121.881 151.07,120.033 151.92,118.61C152.32,117.914 152.74,117.21 153.12,116.501C154.03,114.839 154.88,113.15 155.67,111.434L155.67,111.433C156.46,109.712 157.17,107.966 157.81,106.198L157.79,106.197C158.52,104.189 159.16,102.152 159.71,100.096C160.2,98.276 160.62,96.438 160.95,94.588L160.95,94.589C161.25,92.978 161.49,91.357 161.68,89.728L161.68,89.727C161.89,87.86 162.04,85.983 162.12,84.102L162.11,84.102C162.18,81.972 162.18,79.837 162.08,77.702C161.97,75.82 161.8,73.939 161.56,72.062C161.35,70.437 161.09,68.815 160.77,67.198L160.75,67.198C160.72,67.058 160.69,66.919 160.66,66.78C160.66,66.674 160.63,66.568 160.61,66.462C160.44,65.679 160.27,64.896 160.07,64.115C159.67,62.51 160.66,60.882 162.28,60.493C162.47,60.444 162.67,60.418 162.87,60.41Z").toPath())
            put("p3a_small", parser.parsePathString("M41.5,134.113C42.51,135.434 42.25,137.313 40.92,138.312C39.59,139.311 37.72,139.049 36.72,137.719L35.13,135.628L35.13,135.627L34.61,134.911L31.49,130.778L27.29,125.218L24.19,121.091L22.14,118.364C21.13,117.043 21.39,115.163 22.73,114.164C23.3,113.727 23.99,113.532 24.66,113.562C25.53,113.599 26.36,114.009 26.93,114.757L28.92,117.404L32.08,121.606L32.38,121.991L32.62,122.324L35.74,126.452L35.8,126.531L36.28,127.169L39.86,131.912L41.5,134.113Z").toPath())

            // --- Phone (4a) ---
            put("p4a_bar", parser.parsePathString("M40.5,300.5L142.5,300.5").toPath())
            put("p4a_dot", parser.parsePathString("M91,330.5A5,5 0 1,1 90.99,330.5Z").toPath())

            // --- Phone (4b) ---
            put("p4b_island", Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 10f,
                        top = 10f,
                        right = 172f,
                        bottom = 160f,
                        cornerRadius = CornerRadius(24f)
                    )
                )
            })
            put("p4b_bar", parser.parsePathString("M144,50h14v100h-14z").toPath())
            
            // --- Phone (1) & (2) Camera Plate ---
            val p12CamRadius = 28f
            val p12CamX = 6f
            val p12CamY = 7f
            put("p12_cam_plate", Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = p12CamX,
                        top = p12CamY,
                        right = p12CamX + 52f,
                        bottom = p12CamY + 86f,
                        cornerRadius = CornerRadius(p12CamRadius)
                    )
                )
            })

            // --- Phone (2) Camera Plate (Taller) ---
            put("p2_cam_plate", Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = p12CamX,
                        top = p12CamY,
                        right = p12CamX + 52f,
                        bottom = p12CamY + 90f,
                        cornerRadius = CornerRadius(p12CamRadius)
                    )
                )
            })

            // --- Phone (4a) Pro Camera Bump ---
            val p4apCamRadius = 28f
            put("p4ap_island", Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 5.5f,
                        top = 5f,
                        right = 176.5f,
                        bottom = 135f,
                        cornerRadius = CornerRadius(p4apCamRadius)
                    )
                )
            })
            
            // --- Phone (3a) Camera Plate ---
            val p3aCamRadius = 18f
            put("p3a_cam_plate", Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 78f,
                        top = 57f,
                        right = 122f, // 78 + 26 + 18
                        bottom = 93f, // 57 + 36
                        cornerRadius = CornerRadius(p3aCamRadius)
                    )
                )
            })

            // --- Phone (3a) Other Details (Hidden in Preview) ---
            put("p3a_cam_ring", parser.parsePathString("M91,21c29.8,0 54,24.2 54,54s-24.2,54 -54,54s-54,-24.2 -54,-54s24.2,-54 54,-54zm0,1c-29.3,0 -53,23.7 -53,53s23.7,53 53,53s53,-23.7 53,-53s-23.7,-53 -53,-53z").toPath())
            put("p3a_cam_lens_l", parser.parsePathString("M78,67a8,8 0 1,0 0,16a8,8 0 1,0 0,-16z").toPath())
            put("p3a_cam_lens_r", parser.parsePathString("M104,67a8,8 0 1,0 0,16a8,8 0 1,0 0,-16z").toPath())
            put("p3a_cam_flash", parser.parsePathString("M118,45a3,3 0 1,0 0,6a3,3 0 1,0 0,-6z").toPath())
            put("p3a_cam_tracks", parser.parsePathString("M60,140h62M60,144h62M60,148h62M60,152h62").toPath())
        }
    }

    // Optimization: Memoize visible LED data for matrix devices
    val matrixData = remember(device) {
        if (device != DeviceProfile.DEVICE_NP3 && device != DeviceProfile.DEVICE_NP4APRO) return@remember null

        val matrixW = if (device == DeviceProfile.DEVICE_NP3) 25 else 13
        val matrixH = if (device == DeviceProfile.DEVICE_NP3) 25 else 13
        val pixelSize = if (device == DeviceProfile.DEVICE_NP3) 13.05f else 30f
        val pixelGap = (440f - (matrixW * pixelSize)) / (matrixW - 1)
        val startX = 36f
        val startY = 36f
        val centerX = 256f
        val centerY = 256f
        val radiusSq = when (device) {
            DeviceProfile.DEVICE_NP3 -> 221f * 221f
            DeviceProfile.DEVICE_NP4APRO -> 218.8f * 218.8f
            else -> 220f * 220f
        }

        val visibleLeds = mutableListOf<Triple<Int, Offset, Size>>()
        for (idx in 0 until (matrixW * matrixH)) {
            val row = idx / matrixW
            val col = idx % matrixW
            val px = startX + col * (pixelSize + pixelGap)
            val py = startY + row * (pixelSize + pixelGap)
            val ledCenterX = px + pixelSize / 2f
            val ledCenterY = py + pixelSize / 2f
            val dxC = ledCenterX - centerX
            val dyC = ledCenterY - centerY
            if (dxC * dxC + dyC * dyC <= radiusSq) {
                visibleLeds.add(Triple(idx, Offset(px, py), Size(pixelSize, pixelSize)))
            }
        }
        visibleLeds
    }

    val glowPaint = remember { 
        Paint().apply { 
            isAntiAlias = true 
        } 
    }

    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(if (device == DeviceProfile.DEVICE_NP4A || device == DeviceProfile.DEVICE_NP4B) 16.dp else 40.dp))
            .background(Color(0xFF0A0A0A))
            // 1. Reduced inner padding for 4b / 4z / Phone (1)
            .padding(
                when (device) {
                    DeviceProfile.DEVICE_NP4A, DeviceProfile.DEVICE_NP4B -> 2.dp
                    DeviceProfile.DEVICE_NP1 -> 12.dp
                    else -> 24.dp
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(0.dp)) {
            val vizState = vizStateProvider()
            val viewBoxW = when (device) {
                DeviceProfile.DEVICE_NP3,
                DeviceProfile.DEVICE_NP4APRO -> 512f
                DeviceProfile.DEVICE_NP1 -> 182f
                DeviceProfile.DEVICE_NP4A,
                DeviceProfile.DEVICE_NP4B -> 80f
                else -> 182f
            }
            val viewBoxH = when (device) {
                DeviceProfile.DEVICE_NP2 -> 390f
                DeviceProfile.DEVICE_NP1,
                DeviceProfile.DEVICE_NP4A,
                DeviceProfile.DEVICE_NP4B -> 382f
                DeviceProfile.DEVICE_NP3,
                DeviceProfile.DEVICE_NP4APRO -> 512f
                else -> 182f
            }
            val scale = min(size.width / viewBoxW, size.height / viewBoxH)
            val dx = (size.width - viewBoxW * scale) / 2
            val dy = (size.height - viewBoxH * scale) / 2
            val vbCenter = Offset(viewBoxW / 2f, viewBoxH / 2f)

            // Calculate the horizontal centering offset for legacy models in square viewbox
            // Phone width was 182.
            val centeringOffset = 0f

            fun getA(idx: Int): Float {
                val value = vizState.getOrElse(idx) { 0f }
                return baseOpacity + (value * (1f - baseOpacity))
            }

            fun drawSmoothPath(path: Path, alpha: Float) {
                if (alpha > baseOpacity) {
                    drawIntoCanvas { canvas ->
                        glowPaint.color = color
                        glowPaint.alpha = alpha * 0.45f
                        if (scale > 0f) {
                            glowPaint.nativePaint.maskFilter = android.graphics.BlurMaskFilter(12f * scale, android.graphics.BlurMaskFilter.Blur.NORMAL)
                        }
                        canvas.drawPath(path, glowPaint)
                    }
                }
                drawPath(path, color.copy(alpha = alpha))
            }

            withTransform({
                translate(dx, dy)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                // --- Device Silhouette ---
                val silhouetteRect = Rect(0f, 0f, viewBoxW, viewBoxH)
                val silhouettePath = Path().apply {
                    addRoundRect(androidx.compose.ui.geometry.RoundRect(silhouetteRect, CornerRadius(32f)))
                }

                val showFrame = when (device) {
                    DeviceProfile.DEVICE_NP1,
                    DeviceProfile.DEVICE_NP3,
                    DeviceProfile.DEVICE_NP3A,
                    DeviceProfile.DEVICE_NP2A,
                    DeviceProfile.DEVICE_NP4A,
                    DeviceProfile.DEVICE_NP4B,
                    DeviceProfile.DEVICE_NP4APRO -> false
                    else -> true
                }

                if (showFrame) {
                    drawPath(silhouettePath, Color(0xFF222222), style = Stroke(width = 4f))
                }

                when (device) {
                    DeviceProfile.DEVICE_NP1 -> {
                        withTransform({ translate(centeringOffset, 0f) }) {
                            val localVbCenter = vbCenter.copy(x = vbCenter.x - centeringOffset)
                            if (vizState.size <= 5) {
                                paths["p1_cam"]?.let { drawSmoothPath(it, getA(0)) }
                                paths["p1_slash"]?.let { drawSmoothPath(it, getA(1)) }
                                paths["p1s_ring"]?.let { drawSmoothPath(it, getA(2)) }
                                paths["p1_battery"]?.let { drawSmoothPath(it, getA(3)) }
                                paths["p1_dot"]?.let { drawSmoothPath(it, getA(4)) }
                            } else {
                                paths["p1_cam"]?.let { drawSmoothPath(it, getA(0)) }
                                paths["p1_slash"]?.let { drawSmoothPath(it, getA(1)) }
                                paths["p1_ring_bl"]?.let { drawSmoothPath(it, getA(2)) }
                                paths["p1_ring_br"]?.let { drawSmoothPath(it, getA(3)) }
                                paths["p1_ring_tr"]?.let { drawSmoothPath(it, getA(4)) }
                                paths["p1_ring_tl"]?.let { drawSmoothPath(it, getA(5)) }
                                paths["p1_dot"]?.let { drawSmoothPath(it, getA(6)) }
                                paths["p1_battery"]?.let { drawPathVerticalSegments(this, it, color, 7..14, vizState, baseOpacity, scale, glowPaint) }
                            }
                        }
                    }

                    DeviceProfile.DEVICE_NP2 -> {
                        withTransform({ translate(0f, 6f) }) {
                            withTransform({ translate(3f, -6f) }) {
                                paths["p2_cam_plate"]?.let { drawPath(it, Color.White.copy(alpha = 0.05f)) }
                            }
                            paths["p2_0"]?.let { drawSmoothPath(it, getA(0)) }
                            paths["p2_1"]?.let { drawSmoothPath(it, getA(1)) }
                            paths["p2_2"]?.let { drawSmoothPath(it, getA(2)) }
                            paths["p2_ring"]?.let { drawPathRadial(this, it, color, (3..18).toList(), vizState, baseOpacity, scale, glowPaint, vbCenter.copy(y = vbCenter.y - 6f)) }
                            for (i in 19..24) { paths["p2_$i"]?.let { drawSmoothPath(it, getA(i)) } }
                            paths["p2_battery"]?.let { drawPathVerticalSegments(this, it, color, 25..32, vizState, baseOpacity, scale, glowPaint) }
                        }
                    }

                    DeviceProfile.DEVICE_NP2A -> {
                        // All glyphs fit in ~130x112 area. (31..160 x 55..166)
                        // Center is (95.5, 110.5). Centering it in a 182x182 box.
                        withTransform({ translate(-4.5f, -19.5f) }) {
                            val localVbCenter = Offset(95.5f, 110.5f)
                            paths["p2a_large"]?.let { drawPathRadial(this, it, color, (0..23).toList(), vizState, baseOpacity, scale, glowPaint, localVbCenter) }
                            paths["p2a_medium"]?.let { drawSmoothPath(it, getA(24)) }
                            paths["p2a_small"]?.let { drawSmoothPath(it, getA(25)) }
                        }
                    }

                    DeviceProfile.DEVICE_NP3A -> {
                        // All glyphs fit in ~151x124 area. (17..168 x 14..138)
                        // Center is (92.5, 76). Centering it in a 182x182 box.
                        withTransform({ translate(-1.5f, 15f) }) {
                            val localVbCenter = Offset(92.5f, 76f)
                            paths["p3a_large"]?.let { drawPathRadial(this, it, color, (0..19).toList(), vizState, baseOpacity, scale, glowPaint, localVbCenter) }
                            paths["p3a_medium"]?.let { drawPathRadial(this, it, color, (20..30).toList(), vizState, baseOpacity, scale, glowPaint, localVbCenter) }
                            paths["p3a_small"]?.let { drawPathRadial(this, it, color, (31..35).toList(), vizState, baseOpacity, scale, glowPaint, localVbCenter) }
                        }
                    }

                    DeviceProfile.DEVICE_NP4A, DeviceProfile.DEVICE_NP4B -> {
                        val count = if (device == DeviceProfile.DEVICE_NP4A) 7 else 5
                        val spacing = 6f
                        val availableH = viewBoxH - spacing * 2
                        val squareSize = availableH / count
                        val actualSquareSize = squareSize - 2f

                        for (i in 0 until count) {
                            val alpha = getA(i)
                            val isLast = i == count - 1
                            val squareColor = if (isLast) Color.Red else color
                            val px = (viewBoxW - actualSquareSize) / 2f
                            val py = spacing + i * squareSize

                            if (alpha > baseOpacity) {
                                drawIntoCanvas { canvas ->
                                    glowPaint.color = squareColor
                                    glowPaint.alpha = alpha * 0.4f
                                    if (scale > 0f) {
                                        glowPaint.nativePaint.maskFilter = android.graphics.BlurMaskFilter(8f * scale, android.graphics.BlurMaskFilter.Blur.NORMAL)
                                    }
                                    canvas.drawRect(px, py, px + actualSquareSize, py + actualSquareSize, glowPaint)
                                }
                            }
                            drawRect(color = squareColor, topLeft = Offset(px, py), size = Size(actualSquareSize, actualSquareSize), alpha = alpha)
                        }
                    }

                    DeviceProfile.DEVICE_NP3, DeviceProfile.DEVICE_NP4APRO -> {
                        drawCircle(color = Color.Black, radius = 256f, center = Offset(256f, 256f))
                        matrixData?.forEach { (idx, pos, ledSize) ->
                            val value = vizState.getOrElse(idx) { 0f }
                            val a = 0.11f + (value * 0.89f)
                            if (value > 0) {
                                drawIntoCanvas { canvas ->
                                    glowPaint.color = Color.White
                                    glowPaint.alpha = a * 0.4f
                                    if (scale > 0f) {
                                        glowPaint.nativePaint.maskFilter = android.graphics.BlurMaskFilter(6f * scale, android.graphics.BlurMaskFilter.Blur.NORMAL)
                                    }
                                    canvas.drawRect(pos.x, pos.y, pos.x + ledSize.width, pos.y + ledSize.height, glowPaint)
                                }
                            }
                            drawRect(color = if (value > 0) Color.White else Color(0xFF1C1C1C), topLeft = pos, size = ledSize, alpha = if (value > 0) a else 1f)
                        }
                    }
                }
            }
        }
    }
}

// --- Radial and Linear Drawing Helpers ---

private fun drawPathRadial(
    scope: DrawScope,
    path: Path,
    color: Color,
    indices: List<Int>,
    state: FloatArray,
    baseOpacity: Float,
    scale: Float,
    paint: Paint,
    viewBoxCenter: Offset,
    clockwise: Boolean = true
) {
    val count = indices.size
    if (count <= 0) return

    val androidPath = path.asAndroidPath()
    val pm = PathMeasure(androidPath, false)
    val len = pm.length
    if (len <= 0) return

    val points = mutableListOf<Float>()
    val pos = FloatArray(2)
    val steps = 50
    for (i in 0..steps) {
        pm.getPosTan(len * i / steps, pos, null)
        val angle = Math.toDegrees(atan2((pos[1] - viewBoxCenter.y).toDouble(), (pos[0] - viewBoxCenter.x).toDouble())).toFloat()
        points.add(angle)
    }

    val sorted = points.sorted()
    var maxGap = 0f
    var gapStart = sorted.last()

    for (i in 0 until sorted.size) {
        val a1 = if (i == 0) sorted.last() else sorted[i - 1]
        val a2 = sorted[i]
        val gap = if (i == 0) (360f - (a1 - a2)) else (a2 - a1)
        if (gap > maxGap) {
            maxGap = gap
            gapStart = a1
        }
    }

    val pathStartAngle = gapStart + maxGap
    val pathSweepAngle = 360f - maxGap
    val segmentSweep = pathSweepAngle / count
    val radius = 1000f // Large enough to cover anything

    indices.forEachIndexed { i, idx ->
        val alpha = baseOpacity + (state.getOrElse(idx) { 0f } * (1f - baseOpacity))

        val angle = if (clockwise) {
            pathStartAngle + (i * segmentSweep)
        } else {
            pathStartAngle + pathSweepAngle - ((i + 1) * segmentSweep)
        }

        val sectorPath = Path().apply {
            moveTo(viewBoxCenter.x, viewBoxCenter.y)
            arcTo(
                rect = Rect(viewBoxCenter.x - radius, viewBoxCenter.y - radius, viewBoxCenter.x + radius, viewBoxCenter.y + radius),
                startAngleDegrees = angle,
                sweepAngleDegrees = segmentSweep,
                forceMoveTo = false
            )
            lineTo(viewBoxCenter.x, viewBoxCenter.y)
            close()
        }

        scope.drawIntoCanvas { canvas ->
            canvas.save()
            canvas.clipPath(sectorPath)

            paint.color = color
            paint.alpha = alpha * 0.4f
            if (scale > 0f) {
                paint.nativePaint.maskFilter = android.graphics.BlurMaskFilter(8f * scale, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawPath(path, paint)

            paint.alpha = alpha
            paint.nativePaint.maskFilter = null
            canvas.drawPath(path, paint)

            canvas.restore()
        }
    }
}

private fun drawPathVerticalSegments(scope: DrawScope, path: Path, color: Color, range: IntRange, state: FloatArray, baseOpacity: Float, scale: Float, paint: Paint) {
    val b = path.getBounds()
    val count = range.last - range.first + 1
    val sliceH = b.height / count

    for (i in 0 until count) {
        val idx = range.first + i
        val alpha = baseOpacity + (state.getOrElse(idx) { 0f } * (1f - baseOpacity))

        scope.drawIntoCanvas { canvas ->
            canvas.save()

            canvas.clipRect(
                left = b.left,
                top = b.bottom - (i + 1) * sliceH,
                right = b.right,
                bottom = b.bottom - i * sliceH
            )

            paint.color = color
            paint.alpha = alpha * 0.4f
            if (scale > 0f) {
                paint.nativePaint.maskFilter = android.graphics.BlurMaskFilter(8f * scale, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawPath(path, paint)

            paint.alpha = alpha
            paint.nativePaint.maskFilter = null
            canvas.drawPath(path, paint)

            canvas.restore()
        }
    }
}
