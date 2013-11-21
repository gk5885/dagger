package coffee;

class ElectricHeater implements Heater {
  boolean heating;

  @Override public void on() {
    System.out.println("\uD83D\uDD25 heating \uD83D\uDD25");
    this.heating = true;
  }

  @Override public void off() {
    this.heating = false;
  }

  @Override public boolean isHot() {
    return heating;
  }
}
