Feature: Managing a hotel booking
  A guest books a stay, changes it, and cancels it. Each scenario cleans up after itself.

  Scenario: A guest books a stay and it is stored as requested
    Given a guest named "Asha Rao" wants 3 nights with the deposit paid
    When they book the stay
    Then the booking is stored exactly as requested

  Scenario: Changing the price leaves the rest of the booking alone
    Given an existing booking for "Asha Rao" at 150
    When the price is changed to 220
    Then the booking costs 220 and the guest's details are unchanged

  Scenario: A cancelled booking can no longer be read
    Given an existing booking for "Vikram Iyer" at 300
    When the booking is cancelled
    Then the booking is gone

  Scenario: Only an authenticated user can cancel a booking
    Given an existing booking for "Meera Nair" at 90
    When someone without a token tries to cancel it
    Then the request is refused with status 403
    And the booking still exists

  Scenario Outline: Guests with different needs can book
    Given a guest named "<guest>" wants <nights> nights with the deposit <deposit>
    And they ask for "<needs>"
    When they book the stay
    Then the booking is stored exactly as requested

    Examples:
      | guest       | nights | deposit  | needs         |
      | Kabir Shah  | 1      | paid     | Breakfast     |
      | Zoya Khan   | 7      | not paid | Late checkout |
      | Rohan Das   | 14     | paid     | Dinner        |
