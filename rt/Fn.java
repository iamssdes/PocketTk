package apple.rt;

/** Any callable value in a transpiled program (def, lambda, bound method). */
public interface Fn {
    Object call(Object... a);
}
