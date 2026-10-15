package cloud.jengu.promise.gradle;

import cloud.jengu.promise.Cites;
import cloud.jengu.promise.ToolsetPromises;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** How the plugin's tests say which of the toolset's promises they prove. */
@Cites
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ProvingToolset {
    ToolsetPromises[] value();
}
