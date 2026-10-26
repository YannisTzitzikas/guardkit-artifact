"""guardkit: predicate-based algorithm dispatch with complexity contracts."""
from .core import Guard, Dispatcher, Outcome, Rejected, expected_verification_cost, ratio_order

__all__ = ["Guard", "Dispatcher", "Outcome", "Rejected", "expected_verification_cost", "ratio_order"]
__version__ = "0.1.0"
