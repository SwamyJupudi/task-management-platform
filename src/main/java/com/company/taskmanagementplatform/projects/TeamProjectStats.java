package com.company.taskmanagementplatform.projects;

import java.util.UUID;

/**
 * One team's projects, counted and averaged, for the team performance figure.
 *
 * <p>The average is of the derived {@code progress} column and is computed in {@code numeric} rather
 * than in floating point, matching the progress statement itself, so a team whose projects are all
 * finished reads a hundred rather than ninety-nine point nine.
 *
 * <p>A project with no team is not represented here. The requirements ask for team performance, and
 * inventing an "unassigned" team to hold the remainder would put a row in a list of teams that names
 * nothing anybody can open.
 */
public record TeamProjectStats(UUID teamId, long projectCount, int averageProgress) {}
