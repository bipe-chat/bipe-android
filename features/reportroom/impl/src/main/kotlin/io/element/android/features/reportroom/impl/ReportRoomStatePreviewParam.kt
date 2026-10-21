/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.reportroom.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.architecture.AsyncAction

open class ReportRoomStatePreviewParam : PreviewParameterProvider<ReportRoomState> {
    companion object {
        private const val A_REPORT_ROOM_REASON = "Inappropriate content"
    }

    override val values: Sequence<ReportRoomState>
        get() = sequenceOf(
            aReportRoomState(),
            aReportRoomState(reason = A_REPORT_ROOM_REASON),
            aReportRoomState(leaveRoom = true),
            aReportRoomState(reason = A_REPORT_ROOM_REASON, reportAction = AsyncAction.Loading),
            aReportRoomState(reason = A_REPORT_ROOM_REASON, reportAction = AsyncAction.Failure(Exception("Failed to report"))),
            // Bipe: a DM (bipe#507)
            aReportRoomState(isDm = true),
            aReportRoomState(isDm = true, reason = A_REPORT_ROOM_REASON, reportAction = AsyncAction.Failure(ReportRoom.Exception.LeftRoomFailed)),
        )
}

fun aReportRoomState(
    reason: String = "",
    isDm: Boolean = false,
    leaveRoom: Boolean = false,
    reportAction: AsyncAction<Unit> = AsyncAction.Uninitialized,
    eventSink: (ReportRoomEvent) -> Unit = {}
) = ReportRoomState(
    reason = reason,
    isDm = isDm,
    leaveRoom = leaveRoom,
    reportAction = reportAction,
    eventSink = eventSink,
)
