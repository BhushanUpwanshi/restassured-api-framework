Feature: Booking validation
  The validation matrix lives in testdata/<env>/booking-validation.json, not in an Examples
  table. A 18-row table of JSON payloads is not something a product owner reads, and a
  generated feature file is neither readable nor diffable. So the matrix stays data, and
  this one scenario runs all of it, reporting every failing row rather than the first.

  Scenario: Every documented payload gets its documented response
    When I submit every booking in dataset "booking-validation"
    Then every case gets its expected response
