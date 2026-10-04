from typing import Any, overload

from typing import TypeVar
T = TypeVar("T")

from typing import Generic
class Box(Generic[T]):
    """A simple generic container."""
    @overload
    def __init__(self) -> None: ...
    @overload
    def __init__(self, value: T) -> None: ...
    def get(self) -> Any:
        """Get the contained value."""
        ...
    def set(self, v: T) -> None:
        """Set the contained value."""
        ...
