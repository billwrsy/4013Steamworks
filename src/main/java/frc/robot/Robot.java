package frc.robot;

//Import for Joystick
import edu.wpi.first.wpilibj.Joystick;
//Import for camera
//import edu.wpi.first.cameraserver.CameraServer;
//TimedRobot structure import
import edu.wpi.first.wpilibj.TimedRobot;
//Import VictorSPX Running as PWM
import edu.wpi.first.wpilibj.motorcontrol.PWMVictorSPX;
//Import TalonSRX running as CAN bus
import com.ctre.phoenix.motorcontrol.can.WPI_TalonSRX;

//WHY DO WE HAVE BOTH PWM AND CAN AND TWO DIFFERENT TYPES OF MOTOR CONTROLLER AARGH

public class Robot extends TimedRobot {

  //Motor controller config, I hate the fact that we have multiple different types
  private WPI_TalonSRX shooter = new WPI_TalonSRX (0);
  private WPI_TalonSRX index = new WPI_TalonSRX (2);
  private PWMVictorSPX intake = new PWMVictorSPX(11);
  private PWMVictorSPX FL = new PWMVictorSPX(13);
  private PWMVictorSPX FR = new PWMVictorSPX(14);
  private PWMVictorSPX BL = new PWMVictorSPX(1);
  private PWMVictorSPX BR = new PWMVictorSPX(0);
//Did I mention the fact that two different methods of controlling random motors SUCKS? No? Well, it does.

  //Config for controllers pretty simple
  private Joystick joy1 = new Joystick(0);
  private Joystick xbox = new Joystick(1);
  //private Joystick joy2 = new Joystick(2);
  

  @Override
  public void robotInit() {
    //CameraServer.startAutomaticCapture();
    //detectAprilTags();
  }

  @Override
  public void robotPeriodic() {}

  @Override
  public void autonomousInit() {}

  @Override
  public void autonomousPeriodic() {}

  @Override
  public void teleopInit() {}

  @Override
  public void teleopPeriodic(){

    //Sets the shooter speed
    double shooterspeed = -joy1.getRawAxis(1);
    //double shooterspeed = joy2.getRawAxis(3);
    shooter.set(shooterspeed);
    //Sets the indexer speed (plus shaped part that spins to agitate the balls and feed them to the shooter)
    double indexspeed = joy1.getRawAxis(3);
    index.set(indexspeed);
    //Sets the intake speed (can be positive or negative)
    double intakespeed = joy1.getRawAxis(1);
    intake.set(intakespeed);

    //Driving controller axis values to set drive, strafe, and rotate
    double drive = -xbox.getRawAxis(1);
    double strafe = xbox.getRawAxis(0);
    double rotation = xbox.getRawAxis(4);

    //These are the lines that deal with the driving and mecanums lol
    FL.set(drive + strafe + rotation);
    FR.set(-drive + strafe + rotation);
    BL.set(drive - strafe + rotation);
    BR.set(-drive - strafe + rotation);

  }

  @Override
  public void disabledInit() {}

  @Override
  public void disabledPeriodic() {}

  @Override
  public void testInit() {}

  @Override
  public void testPeriodic() {}

  @Override
  public void simulationInit() {}

  @Override
  public void simulationPeriodic() {}
}
