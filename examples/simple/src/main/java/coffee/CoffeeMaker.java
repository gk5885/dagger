package coffee;

import javax.inject.Inject;

class CoffeeMaker {
  private final Heater heater; // Don't want to create a possibly costly heater until we need it.
  private final Pump pump;

  @Inject CoffeeMaker(Heater heater, Pump pump) {
    this.heater = heater;
    this.pump = pump;
  }

  public void brew() {
    heater.on();
    pump.pump();
    System.out.println(" \u2615 coffee! \u2615 ");
    heater.off();
  }
}
