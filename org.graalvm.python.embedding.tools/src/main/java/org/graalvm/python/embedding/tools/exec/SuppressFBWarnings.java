package org.graalvm.python.embedding.tools.exec;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
@interface SuppressFBWarnings {
	/**
	 * The set of FindBugs
	 * <a href="http://findbugs.sourceforge.net/bugDescriptions.html">warnings</a>
	 * that are to be suppressed in annotated element. The value can be a bug
	 * category, kind or pattern.
	 */
	java.lang.String[] value() default {};
}
