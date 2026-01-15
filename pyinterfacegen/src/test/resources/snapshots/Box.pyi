from typing import overload

class Box[T]:
    """A simple generic container."""
    @overload
    def __init__(self) -> None: ...
    @overload
    def __init__(self, value: T) -> None: ...
    def get(self) -> T:
        """Get the contained value."""
        ...
    def set(self, v: T) -> None:
        """Set the contained value."""
        ...

