
package hprofdap;

import java.util.function.Function;

public record Either<L, R>(L left, R right) {

    public static <L, R> Either<L, R> left(L left) {
        return new Either<L,R>(left, null);
    }

    public static <L, R> Either<L, R> right(R right) {
        return new Either<L,R>(null, right);
    }

    public <T> T bimap(Function<L, T> mapLeft, Function<R, T> mapRight) {
        if (left == null) {
            return mapRight.apply(right);
        }
        return mapLeft.apply(left);
    }
}
