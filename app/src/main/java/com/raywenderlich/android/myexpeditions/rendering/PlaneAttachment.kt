/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modifications Copyright (c) 2017 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.raywenderlich.android.myexpeditions.rendering

import com.google.ar.core.Anchor
import com.google.ar.core.Plane
import com.google.ar.core.Pose

/**
 * This class tracks the attachment of object's Anchor to a Plane. It will construct a pose
 * that will stay on the plane (in Y direction), while still properly tracking the XZ changes
 * from the anchor updates.
 */
class PlaneAttachment(private val plane: Plane, val anchor: Anchor) {

    // Allocate temporary storage to avoid multiple allocations per frame.
    private val mPoseTranslation = FloatArray(3)
    private val mPoseRotation = FloatArray(4)

    /*true if*/ val isTracking: Boolean
        get() =
            plane.trackingState == Plane.TrackingState.TRACKING && anchor.trackingState == Anchor.TrackingState.TRACKING

    val pose: Pose
        get() {
            val pose = anchor.pose
            pose.getTranslation(mPoseTranslation, 0)
            pose.getRotationQuaternion(mPoseRotation, 0)
            mPoseTranslation[1] = plane.centerPose.ty()
            return Pose(mPoseTranslation, mPoseRotation)
        }
}

