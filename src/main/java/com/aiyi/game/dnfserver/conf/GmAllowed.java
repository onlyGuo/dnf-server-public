package com.aiyi.game.dnfserver.conf;

import java.lang.annotation.*;

/** Marks an authenticated endpoint which also permits GMs. All others require super admin. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GmAllowed { }
