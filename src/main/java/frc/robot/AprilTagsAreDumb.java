// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

//FIRST Literally has AprilTag Code?
import edu.wpi.first.apriltag.AprilTagDetection;
import edu.wpi.first.apriltag.AprilTagDetector;
import edu.wpi.first.apriltag.AprilTagFieldLayout;
import edu.wpi.first.apriltag.AprilTagFields;
import edu.wpi.first.apriltag.AprilTagPoseEstimator;
//Starts Camera Stream
import edu.wpi.first.cameraserver.CameraServer;
//CV Shit
import edu.wpi.first.cscore.CvSink;
import edu.wpi.first.cscore.CvSource;
//More Camera
import edu.wpi.first.cscore.UsbCamera;
//CPU Vision via RIO or RIO2
import edu.wpi.first.math.ComputerVisionUtil;
//Maths
import edu.wpi.first.math.geometry.CoordinateSystem;
import edu.wpi.first.math.geometry.Pose3d;
import edu.wpi.first.math.geometry.Rotation3d;
import edu.wpi.first.math.geometry.Transform3d;
import edu.wpi.first.math.geometry.Translation3d;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.networktables.IntegerArrayPublisher;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableInstance;
//Timed Structure
import edu.wpi.first.wpilibj.TimedRobot;

import java.io.IOException;
import java.util.ArrayList;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * This is a demo program showing the detection of AprilTags. The image is acquired from the USB
 * camera, then any detected AprilTags are marked up on the image, transformed to the robot on
 * the field pose and sent to the dashboard.
 *
 * <p>Be aware that the performance on this is much worse than a coprocessor solution!
 */
public class Robot extends TimedRobot {
  @Override
  public void robotInit() {
    var visionThread = new Thread(() -> apriltagVisionThreadProc());
    visionThread.setDaemon(true);
    visionThread.start();
  }

  void apriltagVisionThreadProc() {
    var detector = new AprilTagDetector();
    // look for tag16h5, don't correct any error bits
    detector.addFamily("tag36h11", 0);

    // Positions of AprilTags
    AprilTagFieldLayout aprilTagFieldLayout;
    try {
        aprilTagFieldLayout = AprilTagFieldLayout.loadFromResource(AprilTagFields.k2023ChargedUp.m_resourceFile);
    } catch (IOException e) {
      e.printStackTrace();
      aprilTagFieldLayout = null;
    }

    // Set up Pose Estimator - parameters are for a Microsoft Lifecam HD-3000
    // (https://www.chiefdelphi.com/t/wpilib-apriltagdetector-sample-code/421411/21)
    var poseEstConfig =
        new AprilTagPoseEstimator.Config(
            0.1524, 699.3778103158814, 677.7161226393544, 345.6059345433618, 207.12741326228522);
    var estimator = new AprilTagPoseEstimator(poseEstConfig);

    // Get the UsbCamera from CameraServer
    UsbCamera camera = CameraServer.startAutomaticCapture();
    // Set the resolution
    camera.setResolution(640, 480);

    // Get a CvSink. This will capture Mats from the camera
    CvSink cvSink = CameraServer.getVideo();
    // Setup a CvSource. This will send images back to the Dashboard
    CvSource outputStream = CameraServer.putVideo("Detected", 640, 480);

    // Mats are very memory expensive. Lets reuse these.
    var mat = new Mat();
    var grayMat = new Mat();

    // Instantiate once
    ArrayList<Long> tags = new ArrayList<>();
    var outlineColor = new Scalar(0, 255, 0);
    var crossColor = new Scalar(0, 0, 255);

    // We'll output to NT
    NetworkTable tagsTable = NetworkTableInstance.getDefault().getTable("apriltags");
    IntegerArrayPublisher pubTags = tagsTable.getIntegerArrayTopic("tags").publish();

    // This cannot be 'true'. The program will never exit if it is. This
    // lets the robot stop this thread when restarting robot code or
    // deploying.
    while (!Thread.interrupted()) {
      // Tell the CvSink to grab a frame from the camera and put it
      // in the source mat.  If there is an error notify the output.
      if (cvSink.grabFrame(mat) == 0) {
        // Send the output the error.
        outputStream.notifyError(cvSink.getError());
        // skip the rest of the current iteration
        continue;
      }

      Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_RGB2GRAY);

      AprilTagDetection[] detections = detector.detect(grayMat);

      // have not seen any tags yet
      tags.clear();

      for (AprilTagDetection detection : detections) {

        Pose3d tagInFieldFrame; // pose from WPILib resource

        if(aprilTagFieldLayout.getTagPose(detection.getId()).isPresent() && detection.getDecisionMargin() > 50.) // margin < 20 seems bad; margin > 120 are good
        {
          tagInFieldFrame = aprilTagFieldLayout.getTagPose(detection.getId()).get();
        }
        else
        {
          System.out.println("bad id " + detection.getId() + " " + detection.getDecisionMargin());
          continue;
        }

        // remember we saw this tag
        tags.add((long) detection.getId());

        // draw lines around the tag
        for (var i = 0; i <= 3; i++) {
          var j = (i + 1) % 4;
          var pt1 = new Point(detection.getCornerX(i), detection.getCornerY(i));
          var pt2 = new Point(detection.getCornerX(j), detection.getCornerY(j));
          Imgproc.line(mat, pt1, pt2, outlineColor, 2);
        }

        // mark the center of the tag
        double cx = detection.getCenterX();
        double cy = detection.getCenterY();
        double LL = 10;
        Imgproc.line(mat, new Point(cx - LL, cy), new Point(cx + LL, cy), crossColor, 2);
        Imgproc.line(mat, new Point(cx, cy - LL), new Point(cx, cy + LL), crossColor, 2);

        // identify the tag
        Imgproc.putText(
            mat,
            Integer.toString(detection.getId()),
            new Point(cx + LL, cy),
            Imgproc.FONT_HERSHEY_SIMPLEX,
            1,
            crossColor,
            3);

        // determine pose
        Transform3d pose = estimator.estimate(detection);

        // These transformations are required for the correct robot pose.
        // They arise from the tag facing the camera thus Pi radians rotated or CCW/CW flipped from the
        // mathematically described pose from the estimator that's what our eyes see. The true rotation
        // has to be used to get the right robot pose.
        pose = new Transform3d(
            new Translation3d(
                          pose.getX(),
                          pose.getY(),
                          pose.getZ()),
            new Rotation3d(
                        -pose.getRotation().getX() - Math.PI,
                        -pose.getRotation().getY(),
                         pose.getRotation().getZ() - Math.PI));

        // OpenCV and WPILib estimator layout of axes is EDN and field WPILib is NWU; need x -> -y , y -> -z , z -> x and same for differential rotations
        // pose = CoordinateSystem.convert(pose, CoordinateSystem.EDN(), CoordinateSystem.NWU());
        // WPILib convert is wrong for transforms as of 2023.4.3 so use this patch for now
        {
        // corrected convert
        var from = CoordinateSystem.EDN();
        var to = CoordinateSystem.NWU();
        pose = new Transform3d(
                  CoordinateSystem.convert(pose.getTranslation(), from, to),
                  CoordinateSystem.convert(new Rotation3d(), to, from)
                      .plus(CoordinateSystem.convert(pose.getRotation(), from, to)));
        // end of corrected convert
        }
        
        var // transform to camera from robot chassis center at floor level
        cameraInRobotFrame = new Transform3d(       
        //                      new Translation3d(0., 0., 0.),// camera at center bottom of robot zeros for test data 
        //                      new Rotation3d(0.0, Units.degreesToRadians(0.), Units.degreesToRadians(0.0))); // camera in line with robot chassis
                          new Translation3d(0.2, 0., 0.8),// camera in front of center of robot and above ground
                          new Rotation3d(0.0, Units.degreesToRadians(-30.), Units.degreesToRadians(0.0))); // camera in line with robot chassis
                                                                                 // y = -30 camera points up; +30 points down; sign is correct but backwards of LL

        var // robot in field is the composite of 3 pieces
        robotInFieldFrame = ComputerVisionUtil.objectToRobotPose(tagInFieldFrame,  pose,  cameraInRobotFrame);
        // end transforms to get the robot pose from this vision tag pose

        // put pose into dashboard
        Rotation3d rot = robotInFieldFrame.getRotation();
        tagsTable
            .getEntry("pose_" + detection.getId())
            .setDoubleArray(
                new double[] {
                  robotInFieldFrame.getX(), robotInFieldFrame.getY(), robotInFieldFrame.getZ(), rot.getX(), rot.getY(), rot.getZ()
                });
      }

      // put list of tags onto dashboard
      pubTags.set(tags.stream().mapToLong(Long::longValue).toArray());

      // Give the output stream a new image to display
      outputStream.putFrame(mat);
    }

    pubTags.close();
    detector.close();
  }
}