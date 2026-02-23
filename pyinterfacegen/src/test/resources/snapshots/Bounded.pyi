from numbers import Number

from typing import TypeVar
T = TypeVar("T", bound=Number)

from typing import Generic
class Bounded(Generic[T]):
    """A generic type with an upper bound on the type variable."""
    def __init__(self) -> None: ...
    def id(self, x: T) -> T:
        """Identity function for values of type T."""
        ...
