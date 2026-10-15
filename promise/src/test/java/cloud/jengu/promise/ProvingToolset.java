package cloud.jengu.promise;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** How the toolset's own tests say which of its promises they prove. */
@Cites
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ProvingToolset {
    ToolsetPromises[] value();
}
