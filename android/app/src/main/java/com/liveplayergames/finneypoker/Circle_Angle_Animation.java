package com.liveplayergames.finneypoker;

import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * Created by dbrosen on 1/28/17.
 */

class Circle_Angle_Animation extends Animation {

        private Circle circle;
        private float oldAngle;
        private float newAngle;

        public Circle_Angle_Animation(Circle circle, int newAngle) {
            this.oldAngle = circle.getAngle();
            this.newAngle = newAngle;
            this.circle = circle;
        }

        @Override
        protected void applyTransformation(float interpolatedTime, Transformation transformation) {
            float angle = oldAngle + ((newAngle - oldAngle) * interpolatedTime);
            circle.setAngle(angle);
            circle.requestLayout();
        }
}

