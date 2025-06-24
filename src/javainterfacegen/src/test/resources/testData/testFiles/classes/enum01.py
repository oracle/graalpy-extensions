from enum import Enum

class ArgKind(Enum):
    # Positional argument
    ARG_POS = 0
    # Positional, optional argument (functions only, not calls)
    ARG_OPT = 1
    # *arg argument
    ARG_STAR = 2
    # Keyword argument x=y in call, or keyword-only function arg
    ARG_NAMED = 3
    # **arg argument
    ARG_STAR2 = 4
    # In an argument list, keyword-only and also optional
    ARG_NAMED_OPT = 5

    def is_positional(self, star: bool = False) -> bool:
        return self == ARG_POS or self == ARG_OPT or (star and self == ARG_STAR)

    def is_named(self, star: bool = False) -> bool:
        return self == ARG_NAMED or self == ARG_NAMED_OPT or (star and self == ARG_STAR2)

    def is_required(self) -> bool:
        return self == ARG_POS or self == ARG_NAMED

    def is_optional(self) -> bool:
        return self == ARG_OPT or self == ARG_NAMED_OPT

    def is_star(self) -> bool:
        return self == ARG_STAR or self == ARG_STAR2
