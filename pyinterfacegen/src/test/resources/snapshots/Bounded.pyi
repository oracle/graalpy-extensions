from numbers import Number

class Bounded[T: Number]:
    """A generic type with an upper bound on the type variable."""
    def __init__(self) -> None: ...
    def id(self, x: T) -> T:
        """Identity function for values of type T."""
        ...

