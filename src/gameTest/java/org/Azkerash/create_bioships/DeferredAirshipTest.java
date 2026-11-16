package org.Azkerash.create_bioships;

import java.lang.annotation.*;

/** Preserved prototype scenario; deliberately not registered as a live-stage GameTest. */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
@interface DeferredAirshipTest {
  String template();

  int timeoutTicks() default 100;

  String batch() default "future_airship";
}
