package com.github.cooldood.utils.render.notifications;

import com.github.cooldood.utils.client.MathUtil;
import lombok.Getter;

@Getter
public class Notification {
    private final String title;
    private final String description;
    private final NotificationType type;
    private final long maxTime;
    private final long startTime;

    private float x, y;
    private float targetX, targetY;

    public Notification(String title, String description, NotificationType type, long durationMs) {
        this.title = title;
        this.description = description;
        this.type = type;
        this.maxTime = durationMs;
        this.startTime = System.currentTimeMillis();
        
        // Start positions for animations (off-screen bottom right)
        this.x = 200; // Will be set relative to width in manager
        this.y = 50;  // Will be set relative to height in manager
    }

    public void animate(float targetX, float targetY) {
        this.targetX = targetX;
        this.targetY = targetY;

        // Smoothly interpolate current x/y to target x/y using simple lerp
        this.x = this.x + (targetX - this.x) * 0.1f;
        this.y = this.y + (targetY - this.y) * 0.1f;
    }

    public long getTimeLeft() {
        return maxTime - (System.currentTimeMillis() - startTime);
    }

    public boolean isExpired() {
        return getTimeLeft() <= 0;
    }
}
